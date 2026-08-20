/*
 * ============================================================
 * SPRING BOOT CI/CD
 * ============================================================
 *
 * GitHub
 *   ↓
 * Maven Build + Test
 *   ↓
 * SonarQube
 *   ↓
 * Quality Gate
 *   ↓
 * Nexus Maven
 *   ↓
 * Docker Build
 *   ↓
 * Nexus Docker Registry :8082
 *   ↓
 * K3s Rolling Deployment
 *   ↓
 * Verify
 *   ↓
 * Automatic Rollback
 *
 * ============================================================
 */

def gitRepo   = 'https://github.com/taqin21in/backend-springboot.git'
def gitBranch = 'main'

// ============================================================
// NEXUS MAVEN
// ============================================================

def nexusBaseUrl = 'http://192.168.0.103:8081'

def nexusPublicRepo =
    "${nexusBaseUrl}/repository/maven-public/"

def nexusReleaseRepo =
    "${nexusBaseUrl}/repository/maven-releases/"

def nexusSnapshotRepo =
    "${nexusBaseUrl}/repository/maven-snapshots/"

// ============================================================
// NEXUS DOCKER
// ============================================================

def nexusDockerRegistry = '192.168.0.103:8082'

// ============================================================
// K3S
// ============================================================

def k3sServer     = '192.168.0.104'
def k3sNamespace  = 'backend'
def k3sDeployment = 'backend-springboot'
def k3sKubeconfig = '/home/jenkins/k3s-jenkins.yaml'

// ============================================================
// VARIABLES
// ============================================================

def appName
def appVersion
def dockerTag
def dockerImage
def gitCommitId
def isSnapshot = false
def deploymentStarted = false

// ============================================================
// JENKINS
// ============================================================

node('runner') {

    properties([
        disableConcurrentBuilds(
            abortPrevious: false
        ),
        buildDiscarder(
            logRotator(
                numToKeepStr: '20'
            )
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

            stage('01 - Checkout') {

                deleteDir()

                echo '========================================'
                echo 'CHECKOUT SOURCE CODE'
                echo '========================================'

                git(
                    url: gitRepo,
                    branch: gitBranch,
                    credentialsId: 'github-credential'
                )

                gitCommitId = sh(
                    script: 'git rev-parse HEAD',
                    returnStdout: true
                ).trim()

                echo "Git commit : ${gitCommitId}"
            }

            // =================================================
            // 02 - PREPARE NEXUS
            // =================================================

            stage('02 - Prepare Nexus') {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-credential',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {

                    prepareSettingsXml(
                        nexusPublicRepo
                    )

                    addDistributionToPom(
                        nexusReleaseRepo,
                        nexusSnapshotRepo
                    )
                }
            }

            // =================================================
            // 03 - DETERMINE VERSION
            // =================================================

            stage('03 - Determine Version') {

                def pomVersion
                def groupId
                def artifactId

                pomVersion = getFromPom('version')
                groupId    = getFromPom('groupId')
                artifactId = getFromPom('artifactId')

                appName = artifactId

                echo '========================================'
                echo 'APPLICATION INFORMATION'
                echo '========================================'

                echo "GroupId     : ${groupId}"
                echo "ArtifactId  : ${artifactId}"
                echo "POM Version : ${pomVersion}"

                if (pomVersion.endsWith('-SNAPSHOT')) {

                    isSnapshot = true

                    /*
                     * Maven tetap:
                     *
                     * 0.0.1-SNAPSHOT
                     *
                     * Docker:
                     *
                     * 0.0.1-SNAPSHOT-build-54
                     */

                    appVersion = pomVersion

                    dockerTag =
                        "${pomVersion}-build-${env.BUILD_NUMBER}"

                    echo 'Build Type  : SNAPSHOT'
                    echo "Maven Version : ${appVersion}"
                    echo "Docker Tag    : ${dockerTag}"

                } else {

                    isSnapshot = false

                    appVersion =
                        getNextReleaseVersion(
                            nexusReleaseRepo,
                            groupId,
                            artifactId
                        )

                    dockerTag = appVersion

                    echo 'Build Type : RELEASE'
                    echo "New Version: ${appVersion}"

                    withEnv([
                        "NEW_VERSION=${appVersion}"
                    ]) {

                        sh '''
                            set -e

                            export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                            mvn \
                                -s settings.xml \
                                versions:set \
                                -DnewVersion="$NEW_VERSION" \
                                -DgenerateBackupPoms=false
                        '''
                    }

                    def verifiedVersion =
                        getFromPom('version')

                    if (verifiedVersion != appVersion) {

                        error(
                            "Version mismatch: " +
                            "expected=${appVersion}, " +
                            "actual=${verifiedVersion}"
                        )
                    }
                }

                dockerImage =
                    "${nexusDockerRegistry}/${appName}:${dockerTag}"

                echo '----------------------------------------'
                echo "Application   : ${appName}"
                echo "Maven Version : ${appVersion}"
                echo "Docker Tag    : ${dockerTag}"
                echo "Docker Image  : ${dockerImage}"
                echo "Commit        : ${gitCommitId}"
                echo "Build         : ${env.BUILD_NUMBER}"
                echo '----------------------------------------'
            }

            // =================================================
            // 04 - MAVEN BUILD
            // =================================================

            stage('04 - Maven Build & Test') {

                sh '''
                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                    echo "========================================"
                    echo "MAVEN BUILD & TEST"
                    echo "========================================"

                    java -version
                    mvn -version

                    mvn \
                        -s settings.xml \
                        clean \
                        verify
                '''
            }

            // =================================================
            // 05 - SONARQUBE
            // =================================================

            stage('05 - SonarQube') {

                withSonarQubeEnv('SonarQube') {

                    withEnv([
                        "APP_NAME=${appName}",
                        "APP_VERSION=${appVersion}",
                        "GIT_COMMIT_ID=${gitCommitId}"
                    ]) {

                        sh '''
                            set -e

                            export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                            mvn \
                                -s settings.xml \
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
            // 06 - QUALITY GATE
            // =================================================

            stage('06 - Quality Gate') {

                timeout(
                    time: 10,
                    unit: 'MINUTES'
                ) {

                    def qualityGate =
                        waitForQualityGate(
                            abortPipeline: true
                        )

                    echo "Quality Gate: ${qualityGate.status}"

                    if (qualityGate.status != 'OK') {

                        error(
                            "SonarQube Quality Gate FAILED: " +
                            qualityGate.status
                        )
                    }
                }
            }

            // =================================================
            // 07 - MAVEN DEPLOY
            // =================================================

            stage('07 - Deploy Maven to Nexus') {

                echo '========================================'
                echo 'DEPLOY MAVEN TO NEXUS'
                echo '========================================'

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

                        test -f settings.xml

                        echo "Deploying Maven artifact..."

                        mvn \
                            -s settings.xml \
                            deploy \
                            -DskipTests

                        echo "Maven deploy successful."
                    '''
                }
            }

            // =================================================
            // 08 - DOCKER CHECK
            // =================================================

            stage('08 - Docker Check') {

                sh '''
                    set -e

                    docker --version
                    docker info
                '''
            }

            // =================================================
            // 09 - DOCKER BUILD
            // =================================================

            stage('09 - Docker Build') {

                echo "Building Docker image: ${dockerImage}"

                withEnv([
                    "DOCKER_IMAGE=${dockerImage}"
                ]) {

                    sh '''
                        set -e

                        test -f Dockerfile

                        docker build \
                            --pull \
                            -t "$DOCKER_IMAGE" \
                            .

                        docker image inspect "$DOCKER_IMAGE" \
                            --format '{{.RepoTags}}'
                    '''
                }
            }

            // =================================================
            // 10 - DOCKER PUSH
            // =================================================

            stage('10 - Docker Push to Nexus') {

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

                            echo "========================================"
                            echo "DOCKER PUSH"
                            echo "========================================"

                            echo "$NEXUS_PASSWORD" |
                                docker login "$DOCKER_REGISTRY" \
                                --username "$NEXUS_USERNAME" \
                                --password-stdin

                            docker push "$DOCKER_IMAGE"

                            echo ""
                            echo "Docker push successful."

                            docker image inspect "$DOCKER_IMAGE" \
                                --format '{{.RepoTags}}'

                            docker logout "$DOCKER_REGISTRY" || true
                        '''
                    }
                }
            }

            // =================================================
            // 11 - VERIFY DOCKER IMAGE
            // =================================================

            stage('11 - Verify Docker Image') {

                echo '========================================'
                echo 'VERIFY DOCKER IMAGE'
                echo '========================================'

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

                            echo ""
                            echo "Checking Docker registry..."

                            curl \
                                -fsS \
                                -u "$NEXUS_USERNAME:$NEXUS_PASSWORD" \
                                "http://$DOCKER_REGISTRY/v2/" \
                                > /dev/null

                            echo "Registry reachable."

                            echo ""
                            echo "Testing image pull..."

                            docker pull "$DOCKER_IMAGE"

                            echo ""
                            echo "Docker image successfully verified."

                            docker image inspect "$DOCKER_IMAGE" \
                                --format '{{.RepoTags}}'

                            docker logout "$DOCKER_REGISTRY" || true
                        '''
                    }
                }
            }

            // =================================================
            // 12 - K3S CHECK
            // =================================================

            stage('12 - K3s Check') {

                withEnv([
                    "KUBECONFIG_FILE=${k3sKubeconfig}",
                    "K3S_NAMESPACE=${k3sNamespace}",
                    "K3S_DEPLOYMENT=${k3sDeployment}"
                ]) {

                    sh '''
                        set -e

                        test -f "$KUBECONFIG_FILE"

                        export KUBECONFIG="$KUBECONFIG_FILE"

                        echo "========================================"
                        echo "K3S CHECK"
                        echo "========================================"

                        kubectl version --client

                        echo ""
                        echo "Nodes:"

                        kubectl get nodes

                        echo ""
                        echo "Namespace:"

                        kubectl get pods \
                            -n "$K3S_NAMESPACE"

                        echo ""
                        echo "Deployment:"

                        kubectl get deployment \
                            "$K3S_DEPLOYMENT" \
                            -n "$K3S_NAMESPACE"
                    '''
                }
            }

            // =================================================
            // 13 - K3S DEPLOY
            // =================================================

            stage('13 - Deploy to K3s') {

                deploymentStarted = true

                echo '========================================'
                echo 'DEPLOY TO K3S'
                echo '========================================'

                echo "Image: ${dockerImage}"

                withEnv([
                    "KUBECONFIG_FILE=${k3sKubeconfig}",
                    "K3S_NAMESPACE=${k3sNamespace}",
                    "K3S_DEPLOYMENT=${k3sDeployment}",
                    "DOCKER_IMAGE=${dockerImage}"
                ]) {

                    sh '''
                        set -e

                        export KUBECONFIG="$KUBECONFIG_FILE"

                        echo "Current image:"

                        kubectl get deployment \
                            "$K3S_DEPLOYMENT" \
                            -n "$K3S_NAMESPACE" \
                            -o jsonpath='{.spec.template.spec.containers[0].image}'

                        echo ""

                        echo "Updating image..."

                        kubectl set image \
                            deployment/"$K3S_DEPLOYMENT" \
                            "$K3S_DEPLOYMENT=$DOCKER_IMAGE" \
                            -n "$K3S_NAMESPACE"

                        echo ""

                        echo "Waiting for rollout..."

                        kubectl rollout status \
                            deployment/"$K3S_DEPLOYMENT" \
                            -n "$K3S_NAMESPACE" \
                            --timeout=5m

                        echo ""
                        echo "K3s rollout completed."
                    '''
                }
            }

            // =================================================
            // 14 - VERIFY K3S
            // =================================================

            stage('14 - Verify K3s Deployment') {

                withEnv([
                    "KUBECONFIG_FILE=${k3sKubeconfig}",
                    "K3S_NAMESPACE=${k3sNamespace}",
                    "K3S_DEPLOYMENT=${k3sDeployment}",
                    "EXPECTED_IMAGE=${dockerImage}"
                ]) {

                    sh '''
                        set -e

                        export KUBECONFIG="$KUBECONFIG_FILE"

                        echo "========================================"
                        echo "VERIFY K3S DEPLOYMENT"
                        echo "========================================"

                        kubectl get deployment \
                            "$K3S_DEPLOYMENT" \
                            -n "$K3S_NAMESPACE"

                        echo ""
                        echo "Pods:"

                        kubectl get pods \
                            -n "$K3S_NAMESPACE" \
                            -l app="$K3S_DEPLOYMENT" \
                            -o wide

                        echo ""

                        CURRENT_IMAGE=$(kubectl get deployment \
                            "$K3S_DEPLOYMENT" \
                            -n "$K3S_NAMESPACE" \
                            -o jsonpath='{.spec.template.spec.containers[0].image}')

                        echo "Expected:"
                        echo "$EXPECTED_IMAGE"

                        echo ""
                        echo "Actual:"
                        echo "$CURRENT_IMAGE"

                        if [ "$CURRENT_IMAGE" != "$EXPECTED_IMAGE" ]; then

                            echo ""
                            echo "ERROR: IMAGE MISMATCH"

                            exit 1
                        fi

                        READY_REPLICAS=$(kubectl get deployment \
                            "$K3S_DEPLOYMENT" \
                            -n "$K3S_NAMESPACE" \
                            -o jsonpath='{.status.readyReplicas}')

                        DESIRED_REPLICAS=$(kubectl get deployment \
                            "$K3S_DEPLOYMENT" \
                            -n "$K3S_NAMESPACE" \
                            -o jsonpath='{.spec.replicas}')

                        echo ""
                        echo "Ready replicas  : $READY_REPLICAS"
                        echo "Desired replicas: $DESIRED_REPLICAS"

                        if [ "$READY_REPLICAS" != "$DESIRED_REPLICAS" ]; then
                            echo "ERROR: replicas not ready"
                            exit 1
                        fi

                        echo ""
                        echo "K3s verification PASSED."
                    '''
                }
            }

            // =================================================
            // 15 - APPLICATION STATUS
            // =================================================

            stage('15 - Application Status') {

                withEnv([
                    "KUBECONFIG_FILE=${k3sKubeconfig}",
                    "K3S_NAMESPACE=${k3sNamespace}",
                    "K3S_DEPLOYMENT=${k3sDeployment}"
                ]) {

                    sh '''
                        set -e

                        export KUBECONFIG="$KUBECONFIG_FILE"

                        echo "========================================"
                        echo "APPLICATION STATUS"
                        echo "========================================"

                        echo ""
                        echo "Pods:"

                        kubectl get pods \
                            -n "$K3S_NAMESPACE" \
                            -l app="$K3S_DEPLOYMENT" \
                            -o wide

                        echo ""
                        echo "Service:"

                        kubectl get svc \
                            -n "$K3S_NAMESPACE"

                        echo ""
                        echo "Ingress:"

                        kubectl get ingress \
                            -n "$K3S_NAMESPACE"

                        echo ""
                        echo "Deployment:"

                        kubectl get deployment \
                            "$K3S_DEPLOYMENT" \
                            -n "$K3S_NAMESPACE" \
                            -o wide
                    '''
                }
            }

            // =================================================
            // SUCCESS
            // =================================================

            echo ''
            echo '========================================'
            echo 'PIPELINE SUCCESS'
            echo '========================================'

            echo "Application : ${appName}"
            echo "Maven       : ${appVersion}"
            echo "Docker Tag  : ${dockerTag}"
            echo "Docker      : ${dockerImage}"
            echo "Commit      : ${gitCommitId}"
            echo "Build       : ${env.BUILD_NUMBER}"
            echo "K3s         : ${k3sServer}"
            echo "Namespace   : ${k3sNamespace}"
            echo "Deployment  : ${k3sDeployment}"

            echo '========================================'

        } catch (Exception e) {

            echo ''
            echo '========================================'
            echo 'PIPELINE FAILED'
            echo '========================================'

            echo "Build #${env.BUILD_NUMBER} FAILED."

            // =================================================
            // ROLLBACK
            // =================================================

            if (deploymentStarted) {

                echo ''
                echo '========================================'
                echo 'K3S ROLLBACK'
                echo '========================================'

                try {

                    withEnv([
                        "KUBECONFIG_FILE=${k3sKubeconfig}",
                        "K3S_NAMESPACE=${k3sNamespace}",
                        "K3S_DEPLOYMENT=${k3sDeployment}"
                    ]) {

                        sh '''
                            set -e

                            export KUBECONFIG="$KUBECONFIG_FILE"

                            kubectl rollout undo \
                                deployment/"$K3S_DEPLOYMENT" \
                                -n "$K3S_NAMESPACE"

                            kubectl rollout status \
                                deployment/"$K3S_DEPLOYMENT" \
                                -n "$K3S_NAMESPACE" \
                                --timeout=5m

                            echo ""
                            echo "Rollback completed."

                            echo ""
                            echo "Current image:"

                            kubectl get deployment \
                                "$K3S_DEPLOYMENT" \
                                -n "$K3S_NAMESPACE" \
                                -o jsonpath='{.spec.template.spec.containers[0].image}'

                            echo ""
                        '''
                    }

                } catch (Exception rollbackError) {

                    echo ''
                    echo '========================================'
                    echo 'K3S ROLLBACK FAILED'
                    echo '========================================'

                    echo "${rollbackError}"
                }
            }

            echo ''
            echo 'Original pipeline error:'
            echo "${e}"

            echo '========================================'

            throw e

        } finally {

            // =================================================
            // DOCKER CLEANUP
            // =================================================

            if (dockerImage) {

                withEnv([
                    "DOCKER_IMAGE=${dockerImage}"
                ]) {

                    sh '''
                        docker image rm \
                            "$DOCKER_IMAGE" \
                            || true
                    '''
                }
            }

            // =================================================
            // MAVEN CLEANUP
            // =================================================

            sh '''
                rm -f settings.xml || true
            '''

            deleteDir()

            echo 'Workspace cleanup completed.'
        }
    }
}


// ============================================================
// FUNCTION: GET VALUE FROM POM
// ============================================================

def getFromPom(key) {

    withEnv([
        "POM_KEY=${key}"
    ]) {

        return sh(
            returnStdout: true,
            script: '''
                set -e

                export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                mvn \
                    -s settings.xml \
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
// FUNCTION: GET NEXT RELEASE VERSION
// ============================================================

def getNextReleaseVersion(
    nexusReleaseRepo,
    groupId,
    artifactId
) {

    def groupPath =
        groupId.replace('.', '/')

    def metadataUrl =
        "${nexusReleaseRepo}${groupPath}/${artifactId}/maven-metadata.xml"

    withCredentials([
        usernamePassword(
            credentialsId: 'nexus-credential',
            usernameVariable: 'NEXUS_USERNAME',
            passwordVariable: 'NEXUS_PASSWORD'
        )
    ]) {

        withEnv([
            "METADATA_URL=${metadataUrl}"
        ]) {

            def metadata = sh(
                returnStdout: true,
                script: '''
                    set +e

                    curl \
                        -fsS \
                        -u "$NEXUS_USERNAME:$NEXUS_PASSWORD" \
                        "$METADATA_URL"

                    exit 0
                '''
            ).trim()

            if (!metadata) {

                echo 'No existing release found.'
                echo 'Next release: 0.0.1'

                return '0.0.1'
            }

            def versions = []

            def matcher =
                metadata =~ /<version>([^<]+)<\/version>/

            matcher.each {

                def version =
                    it[1].trim()

                if (
                    version ==~ /^\d+\.\d+\.\d+$/
                ) {

                    versions << version
                }
            }

            if (versions.isEmpty()) {

                echo 'No valid release version found.'
                echo 'Next release: 0.0.1'

                return '0.0.1'
            }

            def maxVersion =
                versions.max { a, b ->

                    def pa =
                        a.tokenize('.').collect {
                            it as Integer
                        }

                    def pb =
                        b.tokenize('.').collect {
                            it as Integer
                        }

                    pa <=> pb
                }

            def parts =
                maxVersion.tokenize('.').collect {
                    it as Integer
                }

            def nextVersion =
                "${parts[0]}.${parts[1]}.${parts[2] + 1}"

            echo "Latest release : ${maxVersion}"
            echo "Next release   : ${nextVersion}"

            return nextVersion
        }
    }
}


// ============================================================
// FUNCTION: ADD DISTRIBUTION MANAGEMENT
// ============================================================

def addDistributionToPom(
    nexusReleaseRepo,
    nexusSnapshotRepo
) {

    def pom = 'pom.xml'

    def content =
        readFile(pom)

    if (
        content.contains('<distributionManagement>')
    ) {

        echo 'distributionManagement already exists.'

        return
    }

    def distributionManagement = """
    <distributionManagement>

        <repository>
            <id>nexus-releases</id>
            <name>Nexus Releases</name>
            <url>${nexusReleaseRepo}</url>
        </repository>

        <snapshotRepository>
            <id>nexus-snapshots</id>
            <name>Nexus Snapshots</name>
            <url>${nexusSnapshotRepo}</url>
        </snapshotRepository>

    </distributionManagement>
    """

    def projectEnd =
        content.lastIndexOf('</project>')

    if (projectEnd == -1) {

        error(
            'Invalid pom.xml: </project> not found'
        )
    }

    def newContent =
        content.substring(
            0,
            projectEnd
        ) +
        distributionManagement +
        content.substring(
            projectEnd
        )

    writeFile(
        file: pom,
        text: newContent
    )

    echo 'distributionManagement added.'
}


// ============================================================
// FUNCTION: CREATE SETTINGS.XML
// ============================================================

def prepareSettingsXml(
    nexusPublicRepo
) {

    withEnv([
        "NEXUS_PUBLIC_REPO=${nexusPublicRepo}"
    ]) {

        sh '''
            set -eu

            cat > settings.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>

<settings
    xmlns="http://maven.apache.org/SETTINGS/1.2.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="
        http://maven.apache.org/SETTINGS/1.2.0
        https://maven.apache.org/xsd/settings-1.2.0.xsd">

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
            <name>Nexus Public Repository</name>
            <url>${NEXUS_PUBLIC_REPO}</url>
            <mirrorOf>*</mirrorOf>
        </mirror>

    </mirrors>

</settings>
EOF

            chmod 600 settings.xml

            echo "settings.xml created."
        '''
    }
}