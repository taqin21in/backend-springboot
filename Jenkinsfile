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

def gitRepo = 'https://github.com/taqin21in/backend-springboot.git'
def gitBranch = 'main'


// ============================================================
// NEXUS
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

def k3sServer = '192.168.0.104'
def k3sNamespace = 'backend'
def k3sDeployment = 'backend-springboot'
def k3sContainer = 'backend-springboot'
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

        'JAVA_HOME=/usr/lib/jvm/java-21-openjdk-21.0.12.0.8-1.2.el9_8.x86_64',

        'MAVEN_HOME=/opt/maven',

        'PATH+JAVA=/usr/lib/jvm/java-21-openjdk-21.0.12.0.8-1.2.el9_8.x86_64/bin',

        'PATH+MAVEN=/opt/maven/bin'

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

                def pomVersion =
                    getFromPom('version')

                def groupId =
                    getFromPom('groupId')

                def artifactId =
                    getFromPom('artifactId')

                appName = artifactId


                echo '========================================'
                echo 'APPLICATION INFORMATION'
                echo '========================================'

                echo "GroupId     : ${groupId}"
                echo "ArtifactId  : ${artifactId}"
                echo "POM Version : ${pomVersion}"


                // ------------------------------------------------
                // SNAPSHOT
                // ------------------------------------------------

                if (pomVersion.endsWith('-SNAPSHOT')) {

                    isSnapshot = true

                    appVersion = pomVersion

                    /*
                     * IMPORTANT
                     *
                     * Docker tag dibuat immutable berdasarkan
                     * Jenkins BUILD_NUMBER.
                     *
                     * Contoh:
                     *
                     * 0.0.1-SNAPSHOT-build-51
                     */

                    dockerTag =
                        "${appVersion}-build-${BUILD_NUMBER}"

                    echo 'Build Type : SNAPSHOT'
                    echo "Version    : ${appVersion}"
                    echo "Docker Tag : ${dockerTag}"
                }


                // ------------------------------------------------
                // RELEASE
                // ------------------------------------------------

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


                    dockerTag = appVersion
                }


                dockerImage =
                    "${nexusDockerRegistry}/${appName}:${dockerTag}"


                echo '----------------------------------------'
                echo "Application : ${appName}"
                echo "Maven       : ${appVersion}"
                echo "Docker Tag  : ${dockerTag}"
                echo "Docker Image: ${dockerImage}"
                echo "Snapshot    : ${isSnapshot}"
                echo "Commit      : ${gitCommitId}"
                echo "Build       : ${BUILD_NUMBER}"
                echo '----------------------------------------'
            }


            // =================================================
            // 04 - MAVEN BUILD & TEST
            // =================================================

            stage('04 - Maven Build & Test') {

                sh '''
                    set -e

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
            // 07 - MAVEN DEPLOY TO NEXUS
            // =================================================

            stage('07 - Deploy Maven to Nexus') {

                echo '========================================'
                echo 'DEPLOY MAVEN ARTIFACT'
                echo '========================================'

                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
                echo "Snapshot    : ${isSnapshot}"


                /*
                 * IMPORTANT:
                 *
                 * settings.xml dibuat dengan:
                 *
                 * nexus-releases
                 * nexus-snapshots
                 *
                 * Dan pom.xml menggunakan ID yang sama.
                 *
                 * Maven mencocokkan credential berdasarkan
                 * server.id dengan distributionManagement.id.
                 */


                sh '''

                    set -e

                    echo "Checking Maven settings..."

                    test -f settings.xml

                    echo ""

                    echo "Maven effective settings check..."

                    mvn \
                        -s settings.xml \
                        help:effective-settings \
                        -DshowPasswords=false \
                        > effective-settings.xml

                    echo ""

                    echo "Deploying Maven artifact..."

                    mvn \
                        -s settings.xml \
                        deploy \
                        -DskipTests

                '''
            }


            // =================================================
            // 08 - DOCKER CHECK
            // =================================================

            stage('08 - Docker Check') {

                sh '''

                    set -e

                    echo "========================================"
                    echo "DOCKER CHECK"
                    echo "========================================"

                    docker --version

                    docker info

                '''
            }


            // =================================================
            // 09 - DOCKER BUILD
            // =================================================

            stage('09 - Docker Build') {

                echo '========================================'
                echo 'DOCKER BUILD'
                echo '========================================'

                echo "Image : ${dockerImage}"


                sh """

                    set -e

                    test -f Dockerfile

                    echo "Building Docker image..."

                    docker build \
                        --pull \
                        -t ${dockerImage} \
                        .

                    echo ""

                    echo "Docker image created:"

                    docker image inspect \
                        ${dockerImage} \
                        --format '{{.Id}}'

                """
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

                    echo '========================================'
                    echo 'DOCKER PUSH'
                    echo '========================================'

                    echo "Registry : ${nexusDockerRegistry}"
                    echo "Image    : ${dockerImage}"


                    sh """

                        set -e

                        echo "\\$NEXUS_PASSWORD" |

                            docker login \
                                ${nexusDockerRegistry} \
                                --username "\\$NEXUS_USERNAME" \
                                --password-stdin


                        echo ""

                        echo "Pushing Docker image..."

                        docker push \
                            ${dockerImage}


                        echo ""

                        echo "Docker push completed."

                        docker logout \
                            ${nexusDockerRegistry} || true

                    """
                }
            }


            // =================================================
            // 11 - VERIFY DOCKER IMAGE
            // =================================================

            stage('11 - Verify Docker Image') {

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

                        echo "\\$NEXUS_PASSWORD" |

                            docker login \
                                ${nexusDockerRegistry} \
                                --username "\\$NEXUS_USERNAME" \
                                --password-stdin


                        echo ""

                        echo "Checking image manifest..."

                        docker manifest inspect \
                            ${dockerImage}


                        echo ""

                        echo "Docker image VERIFIED:"

                        echo "${dockerImage}"


                        docker logout \
                            ${nexusDockerRegistry} || true

                    """
                }
            }


            // =================================================
            // 12 - K3S CHECK
            // =================================================

            stage('12 - K3s Check') {

                echo '========================================'
                echo 'K3S CONNECTION CHECK'
                echo '========================================'


                sh """

                    set -e

                    test -f ${k3sKubeconfig}

                    export KUBECONFIG=${k3sKubeconfig}


                    echo "Kubeconfig : ${k3sKubeconfig}"
                    echo "K3s Server : ${k3sServer}"
                    echo "Namespace  : ${k3sNamespace}"
                    echo "Deployment : ${k3sDeployment}"


                    echo ""

                    kubectl version --client


                    echo ""

                    kubectl get nodes


                    echo ""

                    kubectl get pods \
                        -n ${k3sNamespace}


                    echo ""

                    kubectl get deployment \
                        ${k3sDeployment} \
                        -n ${k3sNamespace}

                """
            }


            // =================================================
            // 13 - DEPLOY TO K3S
            // =================================================

            stage('13 - Deploy to K3s') {

                deploymentStarted = true


                echo '========================================'
                echo 'DEPLOY TO K3S'
                echo '========================================'

                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
                echo "Docker Tag  : ${dockerTag}"
                echo "Image       : ${dockerImage}"


                sh """

                    set -e

                    export KUBECONFIG=${k3sKubeconfig}


                    echo "----------------------------------------"
                    echo "CURRENT IMAGE"
                    echo "----------------------------------------"


                    kubectl get deployment \
                        ${k3sDeployment} \
                        -n ${k3sNamespace} \
                        -o jsonpath='{.spec.template.spec.containers[0].image}'


                    echo ""


                    echo "----------------------------------------"
                    echo "UPDATING IMAGE"
                    echo "----------------------------------------"


                    kubectl set image \
                        deployment/${k3sDeployment} \
                        ${k3sContainer}=${dockerImage} \
                        -n ${k3sNamespace}


                    echo ""

                    echo "Image update submitted."


                    echo "----------------------------------------"
                    echo "WAITING ROLLOUT"
                    echo "----------------------------------------"


                    kubectl rollout status \
                        deployment/${k3sDeployment} \
                        -n ${k3sNamespace} \
                        --timeout=5m


                    echo ""

                    echo "K3s rollout completed successfully."

                """
            }


            // =================================================
            // 14 - VERIFY K3S DEPLOYMENT
            // =================================================

            stage('14 - Verify K3s Deployment') {

                sh """

                    set -e

                    export KUBECONFIG=${k3sKubeconfig}


                    echo "========================================"
                    echo "K3S DEPLOYMENT VERIFICATION"
                    echo "========================================"


                    echo ""

                    echo "Deployment:"

                    kubectl get deployment \
                        ${k3sDeployment} \
                        -n ${k3sNamespace}


                    echo ""

                    echo "Pods:"

                    kubectl get pods \
                        -n ${k3sNamespace} \
                        -l app=${k3sDeployment} \
                        -o wide


                    echo ""

                    echo "Current Image:"

                    CURRENT_IMAGE=\\$(

                        kubectl get deployment \
                            ${k3sDeployment} \
                            -n ${k3sNamespace} \
                            -o jsonpath='{.spec.template.spec.containers[0].image}'

                    )


                    echo "\\$CURRENT_IMAGE"


                    if [ "\\$CURRENT_IMAGE" != "${dockerImage}" ]; then

                        echo ""

                        echo "ERROR: Image mismatch."

                        echo "Expected:"
                        echo "${dockerImage}"

                        echo "Actual:"
                        echo "\\$CURRENT_IMAGE"

                        exit 1

                    fi


                    echo ""

                    echo "Image verification PASSED."


                    echo ""

                    echo "Ready Replicas:"

                    kubectl get deployment \
                        ${k3sDeployment} \
                        -n ${k3sNamespace} \
                        -o jsonpath='{.status.readyReplicas}'


                    echo ""

                """
            }


            // =================================================
            // 15 - APPLICATION STATUS
            // =================================================

            stage('15 - Application Status') {

                sh """

                    set -e

                    export KUBECONFIG=${k3sKubeconfig}


                    echo "========================================"
                    echo "APPLICATION STATUS"
                    echo "========================================"


                    echo ""

                    echo "Pods:"

                    kubectl get pods \
                        -n ${k3sNamespace} \
                        -l app=${k3sDeployment} \
                        -o wide


                    echo ""

                    echo "Service:"

                    kubectl get svc \
                        -n ${k3sNamespace}


                    echo ""

                    echo "Ingress:"

                    kubectl get ingress \
                        -n ${k3sNamespace}


                    echo ""

                    echo "Deployment:"

                    kubectl get deployment \
                        ${k3sDeployment} \
                        -n ${k3sNamespace} \
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
            echo "Build       : ${BUILD_NUMBER}"
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

            echo "Build #${BUILD_NUMBER} FAILED."


            // =================================================
            // ROLLBACK
            // =================================================

            if (deploymentStarted) {

                echo ''

                echo '========================================'
                echo 'K3S ROLLBACK'
                echo '========================================'


                try {

                    sh """

                        set +e

                        export KUBECONFIG=${k3sKubeconfig}


                        echo "Attempting Kubernetes rollback..."


                        kubectl rollout undo \
                            deployment/${k3sDeployment} \
                            -n ${k3sNamespace}


                        echo ""

                        echo "Waiting for rollback..."


                        kubectl rollout status \
                            deployment/${k3sDeployment} \
                            -n ${k3sNamespace} \
                            --timeout=5m


                        echo ""

                        echo "K3s rollback completed."


                        echo ""

                        echo "Current deployment image:"


                        kubectl get deployment \
                            ${k3sDeployment} \
                            -n ${k3sNamespace} \
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


            echo ''

            echo 'Original pipeline error:'

            echo "${e}"

            echo '========================================'


            throw e


        } finally {


            // =================================================
            // DOCKER CLEANUP
            // =================================================

            sh """

                if [ -n "${dockerImage ?: ''}" ]; then

                    echo "Cleaning Docker image..."

                    docker image rm \
                        "${dockerImage}" \
                        || true

                fi

            """


            // =================================================
            // MAVEN SETTINGS CLEANUP
            // =================================================

            sh '''

                rm -f settings.xml || true

                rm -f effective-settings.xml || true

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

                curl \
                    -fsS \
                    -u "\\$NEXUS_USERNAME:\\$NEXUS_PASSWORD" \
                    "${metadataUrl}" \
                    || true

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
                version ==~ /^\\d+\\.\\d+\\.\\d+$/
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


    /*
     * Jika sudah ada distributionManagement,
     * jangan membuat duplicate.
     */

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

    echo ''
    echo 'Maven distribution management:'
    echo "Release  : ${nexusReleaseRepo}"
    echo "Snapshot : ${nexusSnapshotRepo}"
}


// ============================================================
// FUNCTION: CREATE SETTINGS.XML
// ============================================================

def prepareSettingsXml(
    nexusPublicRepo
) {

    sh(

        script: '''

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


        <!-- ========================================= -->
        <!-- NEXUS RELEASE -->
        <!-- ========================================= -->

        <server>

            <id>nexus-releases</id>

            <username>${NEXUS_USERNAME}</username>

            <password>${NEXUS_PASSWORD}</password>

        </server>


        <!-- ========================================= -->
        <!-- NEXUS SNAPSHOT -->
        <!-- ========================================= -->

        <server>

            <id>nexus-snapshots</id>

            <username>${NEXUS_USERNAME}</username>

            <password>${NEXUS_PASSWORD}</password>

        </server>


        <!-- ========================================= -->
        <!-- NEXUS PUBLIC MIRROR -->
        <!-- ========================================= -->

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

            <url>''' + nexusPublicRepo + '''</url>

            <mirrorOf>*</mirrorOf>

        </mirror>

    </mirrors>


</settings>

EOF


            chmod 600 settings.xml


            echo "settings.xml created successfully."

        '''

    )
}