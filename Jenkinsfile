/*
 * ============================================================
 * SPRING BOOT CI/CD PIPELINE
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
 * Verify Docker Image
 *   ↓
 * K3s Rolling Deployment
 *   ↓
 * Verify Deployment
 *   ↓
 * Automatic Rollback if Deployment Failed
 *
 * ============================================================
 */


// ============================================================
// CONFIGURATION
// ============================================================

def gitRepo   = 'https://github.com/taqin21in/backend-springboot.git'
def gitBranch = 'main'


// ============================================================
// JAVA / MAVEN
// ============================================================

def javaHome  = '/usr/lib/jvm/java-21-openjdk-21.0.12.0.8-1.2.el9_8.x86_64'
def mavenHome = '/opt/maven'


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
// NEXUS DOCKER REGISTRY
// ============================================================

def nexusDockerRegistry = '192.168.0.103:8082'


// ============================================================
// K3S
// ============================================================

def k3sServer      = '192.168.0.104'
def k3sNamespace   = 'backend'
def k3sDeployment  = 'backend-springboot'

def k3sKubeconfig =
    '/home/jenkins/k3s-jenkins.yaml'

def k3sRegistrySecret =
    'nexus-registry'


// ============================================================
// JENKINS VARIABLES
// ============================================================

def appName = null
def appVersion = null
def dockerTag = null
def dockerImage = null
def gitCommitId = null

def isSnapshot = false
def deploymentStarted = false


// ============================================================
// JENKINS NODE
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


    // ========================================================
    // ENVIRONMENT
    // ========================================================

    withEnv([
        "JAVA_HOME=${javaHome}",
        "MAVEN_HOME=${mavenHome}",
        "PATH=${javaHome}/bin:${mavenHome}/bin:${env.PATH}"
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
                    script: '''
                        set -e
                        git rev-parse HEAD
                    ''',
                    returnStdout: true
                ).trim()

                echo "Git commit : ${gitCommitId}"
            }


            // =================================================
            // 02 - MAVEN ENVIRONMENT CHECK
            // =================================================

            stage('02 - Maven Environment Check') {

                echo '========================================'
                echo 'MAVEN ENVIRONMENT CHECK'
                echo '========================================'

                sh '''
                    set -e

                    echo "JAVA_HOME=${JAVA_HOME}"
                    echo "MAVEN_HOME=${MAVEN_HOME}"
                    echo "PATH=${PATH}"

                    echo ""
                    echo "Java:"
                    java -version

                    echo ""
                    echo "Maven binary:"
                    command -v mvn

                    echo ""
                    echo "Maven:"
                    mvn -version
                '''
            }


            // =================================================
            // 03 - PREPARE NEXUS
            // =================================================

            stage('03 - Prepare Nexus') {

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
            // 04 - DETERMINE VERSION
            // =================================================

            stage('04 - Determine Version') {

                def pomVersion
                def groupId
                def artifactId

                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-credential',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {

                    pomVersion = getFromPom('version')
                    groupId    = getFromPom('groupId')
                    artifactId = getFromPom('artifactId')
                }


                appName = artifactId


                echo '========================================'
                echo 'APPLICATION INFORMATION'
                echo '========================================'

                echo "GroupId     : ${groupId}"
                echo "ArtifactId  : ${artifactId}"
                echo "POM Version : ${pomVersion}"


                // -------------------------------------------------
                // SNAPSHOT
                // -------------------------------------------------

                if (pomVersion.endsWith('-SNAPSHOT')) {

                    isSnapshot = true
                    appVersion = pomVersion

                    echo 'Build Type  : SNAPSHOT'
                    echo "Version     : ${appVersion}"
                }


                // -------------------------------------------------
                // RELEASE
                // -------------------------------------------------

                else {

                    isSnapshot = false

                    appVersion =
                        getNextReleaseVersion(
                            nexusReleaseRepo,
                            groupId,
                            artifactId
                        )

                    echo 'Build Type  : RELEASE'
                    echo "New Version : ${appVersion}"


                    sh """
                        set -e

                        mvn \
                            -s settings.xml \
                            versions:set \
                            -DnewVersion=${appVersion} \
                            -DgenerateBackupPoms=false
                    """


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


                // -------------------------------------------------
                // DOCKER TAG
                // -------------------------------------------------

                dockerTag =
                    "${appVersion}-build-${env.BUILD_NUMBER}"


                dockerImage =
                    "${nexusDockerRegistry}/${appName}:${dockerTag}"


                echo '----------------------------------------'
                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
                echo "Snapshot    : ${isSnapshot}"
                echo "Docker Tag  : ${dockerTag}"
                echo "Docker Image: ${dockerImage}"
                echo "Commit      : ${gitCommitId}"
                echo "Build       : ${env.BUILD_NUMBER}"
                echo '----------------------------------------'
            }


            // =================================================
            // 05 - MAVEN BUILD & TEST
            // =================================================

            stage('05 - Maven Build & Test') {

                echo '========================================'
                echo 'MAVEN BUILD & TEST'
                echo '========================================'

                sh '''
                    set -e

                    echo "Java:"
                    java -version

                    echo ""
                    echo "Maven:"
                    mvn -version

                    echo ""
                    echo "Running Maven clean verify..."

                    mvn \
                        -s settings.xml \
                        clean verify
                '''
            }


            // =================================================
            // 06 - SONARQUBE
            // =================================================

            stage('06 - SonarQube') {

                withSonarQubeEnv('SonarQube') {

                    sh """
                        set -e

                        echo "========================================"
                        echo "SONARQUBE ANALYSIS"
                        echo "========================================"

                        mvn \
                            -s settings.xml \
                            org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar \
                            -Dsonar.projectKey=${appName} \
                            -Dsonar.projectName=${appName} \
                            -Dsonar.projectVersion=${appVersion} \
                            -Dsonar.scm.revision=${gitCommitId}
                    """
                }
            }


            // =================================================
            // 07 - QUALITY GATE
            // =================================================

            stage('07 - Quality Gate') {

                timeout(
                    time: 10,
                    unit: 'MINUTES'
                ) {

                    def qualityGate =
                        waitForQualityGate(
                            abortPipeline: true
                        )


                    echo "Quality Gate : ${qualityGate.status}"


                    if (qualityGate.status != 'OK') {

                        error(
                            "SonarQube Quality Gate FAILED: " +
                            qualityGate.status
                        )
                    }


                    echo 'SonarQube Quality Gate PASSED'
                }
            }


            // =================================================
            // 08 - DEPLOY MAVEN TO NEXUS
            // =================================================

            stage('08 - Deploy Maven to Nexus') {

                echo '========================================'
                echo 'DEPLOY MAVEN ARTIFACT TO NEXUS'
                echo '========================================'

                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
                echo "Snapshot    : ${isSnapshot}"


                sh '''
                    set -e

                    mvn \
                        -s settings.xml \
                        deploy \
                        -DskipTests
                '''
            }


            // =================================================
            // 09 - DOCKER CHECK
            // =================================================

            stage('09 - Docker Check') {

                echo '========================================'
                echo 'DOCKER CHECK'
                echo '========================================'

                sh '''
                    set -e

                    docker --version

                    echo ""
                    echo "Docker info:"

                    docker info
                '''
            }


            // =================================================
            // 10 - DOCKER BUILD
            // =================================================

            stage('10 - Docker Build') {

                echo '========================================'
                echo 'DOCKER BUILD'
                echo '========================================'

                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
                echo "Docker Tag  : ${dockerTag}"
                echo "Docker Image: ${dockerImage}"


                sh """
                    set -e

                    test -f Dockerfile


                    echo "Dockerfile found."


                    echo ""
                    echo "Building Docker image..."


                    docker build \
                        --pull \
                        -t ${dockerImage} \
                        .


                    echo ""
                    echo "Docker image created:"


                    docker image inspect \
                        ${dockerImage} \
                        --format='ID={{.Id}}'


                    echo ""
                    echo "Docker image:"


                    docker images \
                        ${nexusDockerRegistry}/${appName}
                """
            }


            // =================================================
            // 11 - DOCKER PUSH
            // =================================================

            stage('11 - Docker Push to Nexus') {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-credential',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {

                    echo '========================================'
                    echo 'DOCKER PUSH TO NEXUS'
                    echo '========================================'

                    echo "Registry : ${nexusDockerRegistry}"
                    echo "Image    : ${dockerImage}"


                    sh """
                        set -e

                        echo "Logging in to Nexus Docker Registry..."


                        printf '%s' "\$NEXUS_PASSWORD" |
                            docker login \
                                ${nexusDockerRegistry} \
                                --username "\$NEXUS_USERNAME" \
                                --password-stdin


                        echo ""
                        echo "Docker login successful."


                        echo ""
                        echo "Pushing image:"


                        echo "${dockerImage}"


                        docker push \
                            ${dockerImage}


                        echo ""
                        echo "Docker push successful."


                        docker logout \
                            ${nexusDockerRegistry} || true
                    """
                }
            }


            // =================================================
            // 12 - VERIFY DOCKER IMAGE
            // =================================================

            stage('12 - Verify Docker Image') {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-credential',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {

                    echo '========================================'
                    echo 'VERIFY DOCKER IMAGE'
                    echo '========================================'


                    sh """
                        set -e


                        echo "Registry:"
                        echo "${nexusDockerRegistry}"


                        echo ""
                        echo "Image:"
                        echo "${dockerImage}"


                        echo ""
                        echo "Logging in..."


                        printf '%s' "\$NEXUS_PASSWORD" |
                            docker login \
                                ${nexusDockerRegistry} \
                                --username "\$NEXUS_USERNAME" \
                                --password-stdin


                        echo ""
                        echo "Checking Docker manifest..."


                        if docker manifest inspect "${dockerImage}" > /tmp/manifest.json 2>&1
                        then

                            echo "Docker manifest found."

                            cat /tmp/manifest.json

                        else

                            echo "Manifest inspection failed."
                            cat /tmp/manifest.json

                            echo ""
                            echo "Trying Docker pull as secondary verification..."

                            docker pull "${dockerImage}"

                        fi


                        echo ""
                        echo "Verifying local image..."


                        docker image inspect \
                            "${dockerImage}" \
                            --format='ID={{.Id}}'


                        echo ""
                        echo "Docker image verification PASSED."


                        docker logout \
                            ${nexusDockerRegistry} || true


                        rm -f /tmp/manifest.json
                    """
                }
            }


            // =================================================
            // 13 - K3S CHECK
            // =================================================

            stage('13 - K3s Check') {

                echo '========================================'
                echo 'K3S CONNECTION CHECK'
                echo '========================================'


                sh """
                    set -e


                    test -f "${k3sKubeconfig}"


                    export KUBECONFIG="${k3sKubeconfig}"


                    echo "Kubeconfig : ${k3sKubeconfig}"
                    echo "K3s Server : ${k3sServer}"
                    echo "Namespace  : ${k3sNamespace}"
                    echo "Deployment : ${k3sDeployment}"


                    echo ""
                    echo "Kubernetes Client:"


                    kubectl version --client


                    echo ""
                    echo "K3s Nodes:"


                    kubectl get nodes


                    echo ""
                    echo "Backend Namespace:"


                    kubectl get namespace \
                        "${k3sNamespace}"


                    echo ""
                    echo "Current Pods:"


                    kubectl get pods \
                        -n "${k3sNamespace}"


                    echo ""
                    echo "Current Deployment:"


                    kubectl get deployment \
                        "${k3sDeployment}" \
                        -n "${k3sNamespace}"
                """
            }


            // =================================================
            // 14 - CONFIGURE K3S REGISTRY SECRET
            // =================================================

            stage('14 - Configure K3s Registry Secret') {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-credential',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {

                    echo '========================================'
                    echo 'CONFIGURE K3S NEXUS REGISTRY'
                    echo '========================================'


                    sh """
                        set -e


                        export KUBECONFIG="${k3sKubeconfig}"


                        echo "Registry : ${nexusDockerRegistry}"
                        echo "Secret   : ${k3sRegistrySecret}"
                        echo "Namespace: ${k3sNamespace}"


                        echo ""
                        echo "Creating/updating imagePullSecret..."


                        kubectl create secret docker-registry \
                            "${k3sRegistrySecret}" \
                            --docker-server="${nexusDockerRegistry}" \
                            --docker-username="\$NEXUS_USERNAME" \
                            --docker-password="\$NEXUS_PASSWORD" \
                            --namespace="${k3sNamespace}" \
                            --dry-run=client \
                            -o yaml |
                        kubectl apply -f -


                        echo ""
                        echo "Registry secret configured."


                        kubectl get secret \
                            "${k3sRegistrySecret}" \
                            -n "${k3sNamespace}"
                    """
                }
            }


            // =================================================
            // 15 - K3S ROLLING DEPLOYMENT
            // =================================================

            stage('15 - Deploy to K3s') {

                deploymentStarted = true


                echo '========================================'
                echo 'K3S ROLLING DEPLOYMENT'
                echo '========================================'


                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
                echo "Docker Tag  : ${dockerTag}"
                echo "Image       : ${dockerImage}"
                echo "Namespace   : ${k3sNamespace}"
                echo "Deployment  : ${k3sDeployment}"


                sh """
                    set -e


                    export KUBECONFIG="${k3sKubeconfig}"


                    echo "----------------------------------------"
                    echo "Current Image"
                    echo "----------------------------------------"


                    kubectl get deployment \
                        "${k3sDeployment}" \
                        -n "${k3sNamespace}" \
                        -o jsonpath='{.spec.template.spec.containers[0].image}'


                    echo ""


                    echo "----------------------------------------"
                    echo "Updating Image"
                    echo "----------------------------------------"


                    kubectl set image \
                        "deployment/${k3sDeployment}" \
                        "${k3sDeployment}=${dockerImage}" \
                        -n "${k3sNamespace}"


                    echo ""
                    echo "Image update submitted."


                    echo ""
                    echo "----------------------------------------"
                    echo "Waiting for Rollout"
                    echo "----------------------------------------"


                    kubectl rollout status \
                        "deployment/${k3sDeployment}" \
                        -n "${k3sNamespace}" \
                        --timeout=5m


                    echo ""
                    echo "K3s rollout completed successfully."
                """
            }


            // =================================================
            // 16 - VERIFY K3S DEPLOYMENT
            // =================================================

            stage('16 - Verify K3s Deployment') {

                sh """
                    set -e


                    export KUBECONFIG="${k3sKubeconfig}"


                    echo "========================================"
                    echo "K3S DEPLOYMENT VERIFICATION"
                    echo "========================================"


                    echo ""
                    echo "Deployment:"


                    kubectl get deployment \
                        "${k3sDeployment}" \
                        -n "${k3sNamespace}"


                    echo ""
                    echo "Pods:"


                    kubectl get pods \
                        -n "${k3sNamespace}" \
                        -l "app=${k3sDeployment}" \
                        -o wide


                    echo ""
                    echo "Expected Image:"


                    echo "${dockerImage}"


                    echo ""
                    echo "Current Image:"


                    CURRENT_IMAGE=\$(kubectl get deployment \
                        "${k3sDeployment}" \
                        -n "${k3sNamespace}" \
                        -o jsonpath='{.spec.template.spec.containers[0].image}')


                    echo "\${CURRENT_IMAGE}"


                    if [ "\${CURRENT_IMAGE}" != "${dockerImage}" ]
                    then

                        echo ""
                        echo "ERROR: Image mismatch."

                        echo "Expected:"
                        echo "${dockerImage}"

                        echo "Actual:"
                        echo "\${CURRENT_IMAGE}"

                        exit 1

                    fi


                    echo ""
                    echo "Image verification PASSED."


                    READY_REPLICAS=\$(kubectl get deployment \
                        "${k3sDeployment}" \
                        -n "${k3sNamespace}" \
                        -o jsonpath='{.status.readyReplicas}')


                    DESIRED_REPLICAS=\$(kubectl get deployment \
                        "${k3sDeployment}" \
                        -n "${k3sNamespace}" \
                        -o jsonpath='{.spec.replicas}')


                    READY_REPLICAS=\${READY_REPLICAS:-0}
                    DESIRED_REPLICAS=\${DESIRED_REPLICAS:-0}


                    echo ""
                    echo "Ready replicas   : \${READY_REPLICAS}"
                    echo "Desired replicas : \${DESIRED_REPLICAS}"


                    if [ "\${READY_REPLICAS}" -lt "\${DESIRED_REPLICAS}" ]
                    then

                        echo ""
                        echo "ERROR: Not all replicas are ready."

                        exit 1

                    fi


                    echo ""
                    echo "Replica verification PASSED."


                    echo ""
                    echo "Rollout History:"


                    kubectl rollout history \
                        "deployment/${k3sDeployment}" \
                        -n "${k3sNamespace}"
                """
            }


            // =================================================
            // 17 - APPLICATION STATUS
            // =================================================

            stage('17 - Application Status') {

                sh """
                    set -e


                    export KUBECONFIG="${k3sKubeconfig}"


                    echo "========================================"
                    echo "APPLICATION STATUS"
                    echo "========================================"


                    echo ""
                    echo "Pods:"


                    kubectl get pods \
                        -n "${k3sNamespace}" \
                        -l "app=${k3sDeployment}" \
                        -o wide


                    echo ""
                    echo "Services:"


                    kubectl get svc \
                        -n "${k3sNamespace}"


                    echo ""
                    echo "Ingress:"


                    kubectl get ingress \
                        -n "${k3sNamespace}" || true


                    echo ""
                    echo "Deployment:"


                    kubectl get deployment \
                        "${k3sDeployment}" \
                        -n "${k3sNamespace}" \
                        -o wide
                """
            }


            // =================================================
            // SUCCESS
            // =================================================

            echo ''

            echo '========================================'
            echo 'PIPELINE SUCCESS'
            echo '========================================'

            echo "Application : ${appName}"
            echo "Version     : ${appVersion}"
            echo "Docker Tag  : ${dockerTag}"
            echo "Docker      : ${dockerImage}"
            echo "Commit      : ${gitCommitId}"
            echo "Build       : ${env.BUILD_NUMBER}"
            echo "Maven       : Nexus"
            echo "K3s         : ${k3sServer}"
            echo "Namespace   : ${k3sNamespace}"
            echo "Deployment  : ${k3sDeployment}"

            echo '========================================'


        } catch (Exception e) {

            // =================================================
            // PIPELINE FAILED
            // =================================================

            echo ''

            echo '========================================'
            echo 'PIPELINE FAILED'
            echo '========================================'

            echo "Build #${env.BUILD_NUMBER} FAILED."


            // =================================================
            // K3S ROLLBACK
            // =================================================

            if (deploymentStarted) {

                echo ''

                echo '========================================'
                echo 'K3S ROLLBACK'
                echo '========================================'


                try {

                    sh """
                        set -e


                        export KUBECONFIG="${k3sKubeconfig}"


                        echo "Attempting Kubernetes rollback..."


                        kubectl rollout undo \
                            "deployment/${k3sDeployment}" \
                            -n "${k3sNamespace}"


                        echo ""
                        echo "Waiting for rollback..."


                        kubectl rollout status \
                            "deployment/${k3sDeployment}" \
                            -n "${k3sNamespace}" \
                            --timeout=5m


                        echo ""
                        echo "K3s rollback completed."


                        echo ""
                        echo "Current deployment image:"


                        kubectl get deployment \
                            "${k3sDeployment}" \
                            -n "${k3sNamespace}" \
                            -o jsonpath='{.spec.template.spec.containers[0].image}'


                        echo ""
                    """

                } catch (Exception rollbackError) {

                    echo ''

                    echo '========================================'
                    echo 'K3S ROLLBACK FAILED'
                    echo '========================================'

                    echo "Rollback error:"
                    echo "${rollbackError}"

                    echo '========================================'
                }
            }


            // =================================================
            // ORIGINAL ERROR
            // =================================================

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

                sh """
                    echo "Cleaning Docker image..."

                    docker image rm \
                        "${dockerImage}" || true
                """
            }


            // =================================================
            // MAVEN SETTINGS CLEANUP
            // =================================================

            sh '''
                rm -f settings.xml || true
            '''


            // =================================================
            // WORKSPACE CLEANUP
            // =================================================

            deleteDir()

            echo 'Workspace cleanup completed.'
        }
    }
}


// ============================================================
// FUNCTION: GET VALUE FROM POM
// ============================================================

def getFromPom(key) {

    return sh(
        returnStdout: true,
        script: """
            set -e

            mvn \
                -s settings.xml \
                -q \
                org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate \
                -Dexpression=project.${key} \
                -DforceStdout \
                -DskipTests
        """
    ).trim()
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

        def metadata = sh(
            returnStdout: true,
            script: """
                set +e

                curl \
                    -fsS \
                    -u "\$NEXUS_USERNAME:\$NEXUS_PASSWORD" \
                    "${metadataUrl}"

                exit 0
            """
        ).trim()


        if (!metadata) {

            echo 'No existing release found.'
            echo 'Next release: 0.0.1'

            return '0.0.1'
        }


        def versions = []


        def matcher =
            metadata =~ /<version>([^<]+)<\\/version>/


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

    sh(
        script: """
            set -eu


            cat > settings.xml <<'EOF'

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
            <username>\${NEXUS_USERNAME}</username>
            <password>\${NEXUS_PASSWORD}</password>
        </server>

        <server>
            <id>nexus-snapshots</id>
            <username>\${NEXUS_USERNAME}</username>
            <password>\${NEXUS_PASSWORD}</password>
        </server>

        <server>
            <id>nexus-public</id>
            <username>\${NEXUS_USERNAME}</username>
            <password>\${NEXUS_PASSWORD}</password>
        </server>

    </servers>


    <mirrors>

        <mirror>

            <id>nexus-public</id>

            <name>
                Nexus Public Repository
            </name>

            <url>${nexusPublicRepo}</url>

            <mirrorOf>*</mirrorOf>

        </mirror>

    </mirrors>

</settings>

EOF


            chmod 600 settings.xml


            echo "settings.xml created."
        """
    )
}