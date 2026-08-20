/*
 * ============================================================
 * SPRING BOOT CI/CD - FAST VERSION
 *
 * GitHub
 *   ↓
 * Maven Build + Test + SonarQube
 *   ↓
 * Quality Gate
 *   ↓
 * Nexus Maven
 *   ↓
 * Docker Build + Push
 *   ↓
 * K3s Rolling Deployment
 *   ↓
 * Rollback on Failure
 * ============================================================
 */

def gitRepo   = 'https://github.com/taqin21in/backend-springboot.git'
def gitBranch = 'main'

// Nexus
def nexusBaseUrl      = 'http://192.168.0.103:8081'
def nexusPublicRepo   = "${nexusBaseUrl}/repository/maven-public/"
def nexusReleaseRepo  = "${nexusBaseUrl}/repository/maven-releases/"
def nexusSnapshotRepo = "${nexusBaseUrl}/repository/maven-snapshots/"

// Docker
def nexusDockerRegistry = '192.168.0.103:8082'

// K3s
def k3sNamespace  = 'backend'
def k3sDeployment = 'backend-springboot'
def k3sKubeconfig = '/home/jenkins/k3s-jenkins.yaml'

// Variables
def appName
def appVersion
def dockerTag
def dockerImage
def gitCommitId
def deploymentStarted = false


node('runner') {

    properties([
        disableConcurrentBuilds(),
        buildDiscarder(
            logRotator(numToKeepStr: '20')
        )
    ])

    withEnv([
        'JAVA_HOME=/usr/lib/jvm/java-21-openjdk-21.0.12.0.8-1.2.el9_8.x86_64',
        'MAVEN_HOME=/opt/maven'
    ]) {

        try {

            // =================================================
            // 01 - CHECKOUT
            // =================================================

            stage('Checkout') {

                deleteDir()

                git(
                    url: gitRepo,
                    branch: gitBranch,
                    credentialsId: 'github-credential'
                )

                gitCommitId = sh(
                    script: 'git rev-parse HEAD',
                    returnStdout: true
                ).trim()

                echo "Commit: ${gitCommitId}"
            }


            // =================================================
            // 02 - PREPARE NEXUS
            // =================================================

            stage('Prepare Nexus') {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-credential',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {

                    prepareSettingsXml(nexusPublicRepo)

                    addDistributionToPom(
                        nexusReleaseRepo,
                        nexusSnapshotRepo
                    )
                }
            }


            // =================================================
            // 03 - VERSION
            // =================================================

            stage('Version') {

                def pomVersion = getFromPom('version')
                appName = getFromPom('artifactId')

                if (pomVersion.endsWith('-SNAPSHOT')) {

                    appVersion = pomVersion

                    dockerTag =
                        "${pomVersion}-build-${env.BUILD_NUMBER}"

                } else {

                    def groupId = getFromPom('groupId')

                    appVersion = getNextReleaseVersion(
                        nexusReleaseRepo,
                        groupId,
                        appName
                    )

                    dockerTag = appVersion

                    withEnv([
                        "NEW_VERSION=${appVersion}"
                    ]) {

                        sh '''
                            export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                            mvn -s settings.xml \
                                versions:set \
                                -DnewVersion="$NEW_VERSION" \
                                -DgenerateBackupPoms=false
                        '''
                    }
                }

                dockerImage =
                    "${nexusDockerRegistry}/${appName}:${dockerTag}"

                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
                echo "Docker      : ${dockerImage}"
            }


            // =================================================
            // 04 - BUILD + TEST + SONAR
            // =================================================

            stage('Build & SonarQube') {

                withSonarQubeEnv('SonarQube') {

                    withEnv([
                        "APP_NAME=${appName}",
                        "APP_VERSION=${appVersion}",
                        "GIT_COMMIT_ID=${gitCommitId}"
                    ]) {

                        sh '''
                            set -e

                            export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                            mvn -s settings.xml \
                                clean verify \
                                org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar \
                                -Dsonar.projectKey="$APP_NAME" \
                                -Dsonar.projectName="$APP_NAME" \
                                -Dsonar.projectVersion="$APP_VERSION" \
                                -Dsonar.scm.revision="$GIT_COMMIT_ID"
                        '''
                    }
                }
            }


            // =================================================
            // 05 - QUALITY GATE
            // =================================================

            stage('Quality Gate') {

                timeout(
                    time: 10,
                    unit: 'MINUTES'
                ) {

                    def result = waitForQualityGate(
                        abortPipeline: true
                    )

                    if (result.status != 'OK') {
                        error(
                            "Quality Gate FAILED: ${result.status}"
                        )
                    }
                }
            }


            // =================================================
            // 06 - MAVEN DEPLOY
            // =================================================

            stage('Maven Deploy') {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-credential',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {

                    sh '''
                        set -e

                        export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                        mvn -s settings.xml \
                            deploy \
                            -DskipTests
                    '''
                }
            }


            // =================================================
            // 07 - DOCKER BUILD + PUSH
            // =================================================

            stage('Docker Build & Push') {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-credential',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {

                    withEnv([
                        "DOCKER_IMAGE=${dockerImage}",
                        "DOCKER_REGISTRY=${nexusDockerRegistry}"
                    ]) {

                        sh '''
                            set -e

                            echo "$NEXUS_PASSWORD" |
                                docker login "$DOCKER_REGISTRY" \
                                --username "$NEXUS_USERNAME" \
                                --password-stdin

                            docker build \
                                --pull \
                                -t "$DOCKER_IMAGE" \
                                .

                            docker push "$DOCKER_IMAGE"
                        '''
                    }
                }
            }


            // =================================================
            // 08 - K3S DEPLOY
            // =================================================

            stage('Deploy K3s') {

                deploymentStarted = true

                withEnv([
                    "KUBECONFIG=${k3sKubeconfig}",
                    "NAMESPACE=${k3sNamespace}",
                    "DEPLOYMENT=${k3sDeployment}",
                    "DOCKER_IMAGE=${dockerImage}"
                ]) {

                    sh '''
                        set -e

                        kubectl set image \
                            deployment/"$DEPLOYMENT" \
                            "$DEPLOYMENT=$DOCKER_IMAGE" \
                            -n "$NAMESPACE"

                        kubectl rollout status \
                            deployment/"$DEPLOYMENT" \
                            -n "$NAMESPACE" \
                            --timeout=5m
                    '''
                }
            }


            // =================================================
            // SUCCESS
            // =================================================

            echo """
            ========================================
            PIPELINE SUCCESS
            ========================================
            Application : ${appName}
            Version     : ${appVersion}
            Docker      : ${dockerImage}
            Commit      : ${gitCommitId}
            ========================================
            """

        } catch (Exception e) {

            echo "PIPELINE FAILED: ${e}"

            // =================================================
            // ROLLBACK
            // =================================================

            if (deploymentStarted) {

                try {

                    withEnv([
                        "KUBECONFIG=${k3sKubeconfig}",
                        "NAMESPACE=${k3sNamespace}",
                        "DEPLOYMENT=${k3sDeployment}"
                    ]) {

                        sh '''
                            kubectl rollout undo \
                                deployment/"$DEPLOYMENT" \
                                -n "$NAMESPACE"

                            kubectl rollout status \
                                deployment/"$DEPLOYMENT" \
                                -n "$NAMESPACE" \
                                --timeout=5m
                        '''
                    }

                    echo 'Rollback completed.'

                } catch (Exception rollbackError) {

                    echo "Rollback FAILED: ${rollbackError}"
                }
            }

            throw e

        } finally {

            // Cleanup Docker image
            if (dockerImage) {

                sh """
                    docker image rm '${dockerImage}' || true
                """
            }

            // Cleanup workspace
            deleteDir()
        }
    }
}


// ============================================================
// GET VALUE FROM POM
// ============================================================

def getFromPom(key) {

    withEnv([
        "POM_KEY=${key}"
    ]) {

        return sh(
            returnStdout: true,
            script: '''
                export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                mvn -s settings.xml \
                    -q \
                    org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate \
                    -Dexpression=project."$POM_KEY" \
                    -DforceStdout \
                    -DskipTests
            '''
        ).trim()
    }
}


// ============================================================
// GET NEXT RELEASE VERSION
// ============================================================

def getNextReleaseVersion(
    nexusReleaseRepo,
    groupId,
    artifactId
) {

    def groupPath = groupId.replace('.', '/')

    def metadataUrl =
        "${nexusReleaseRepo}${groupPath}/${artifactId}/maven-metadata.xml"

    withCredentials([
        usernamePassword(
            credentialsId: 'nexus-credential',
            usernameVariable: 'NEXUS_USERNAME',
            passwordVariable: 'NEXUS_PASSWORD'
        )
    ]) {

        def metadata = sh(
            returnStdout: true,
            script: """
                curl -fsS \
                    -u "\$NEXUS_USERNAME:\$NEXUS_PASSWORD" \
                    "${metadataUrl}" \
                    || true
            """
        ).trim()

        if (!metadata) {
            return '0.0.1'
        }

        def versions = []

        def matcher =
            metadata =~ /<version>([^<]+)<\\/version>/

        matcher.each {

            def version = it[1].trim()

            if (version ==~ /^\\d+\\.\\d+\\.\\d+$/) {
                versions << version
            }
        }

        if (versions.isEmpty()) {
            return '0.0.1'
        }

        def maxVersion = versions.max { a, b ->

            def pa = a.tokenize('.').collect {
                it as Integer
            }

            def pb = b.tokenize('.').collect {
                it as Integer
            }

            pa <=> pb
        }

        def parts = maxVersion.tokenize('.').collect {
            it as Integer
        }

        return "${parts[0]}.${parts[1]}.${parts[2] + 1}"
    }
}


// ============================================================
// ADD DISTRIBUTION MANAGEMENT
// ============================================================

def addDistributionToPom(
    nexusReleaseRepo,
    nexusSnapshotRepo
) {

    def content = readFile('pom.xml')

    if (content.contains('<distributionManagement>')) {
        return
    }

    def distributionManagement = """

    <distributionManagement>

        <repository>
            <id>nexus-releases</id>
            <url>${nexusReleaseRepo}</url>
        </repository>

        <snapshotRepository>
            <id>nexus-snapshots</id>
            <url>${nexusSnapshotRepo}</url>
        </snapshotRepository>

    </distributionManagement>
    """

    def projectEnd = content.lastIndexOf('</project>')

    if (projectEnd == -1) {
        error('Invalid pom.xml')
    }

    writeFile(
        file: 'pom.xml',
        text:
            content.substring(0, projectEnd) +
            distributionManagement +
            content.substring(projectEnd)
    )
}


// ============================================================
// CREATE SETTINGS.XML
// ============================================================

def prepareSettingsXml(nexusPublicRepo) {

    withEnv([
        "NEXUS_PUBLIC_REPO=${nexusPublicRepo}"
    ]) {

        sh '''
            set -eu

            cat > settings.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>

<settings
    xmlns="http://maven.apache.org/SETTINGS/1.2.0">

    <servers>

        <server>
            <id>nexus-releases</id>
            <username>${NEXUS_USERNAME}</username>
            <password>${NEXUS_PASSWORD}</password>
        </server>

        <server>
            <id>nexus-snapshots</id>
            <username>${NEXUS_USERNAME}</username>
            <password>${NEXUS_PASSWORD}</password>
        </server>

        <server>
            <id>nexus-public</id>
            <username>${NEXUS_USERNAME}</username>
            <password>${NEXUS_PASSWORD}</password>
        </server>

    </servers>

    <mirrors>

        <mirror>
            <id>nexus-public</id>
            <name>Nexus Public</name>
            <url>${NEXUS_PUBLIC_REPO}</url>
            <mirrorOf>*</mirrorOf>
        </mirror>

    </mirrors>

</settings>
EOF

            chmod 600 settings.xml
        '''
    }
}