/*
 * ============================================================
 * SPRING BOOT CI/CD
 * ============================================================
 *
 * GitHub
 *   ↓
 * Checkout
 *   ↓
 * Determine Version
 *   ↓
 * Maven Build + Test
 *   ↓
 * SonarQube
 *   ↓
 * Quality Gate
 *   ↓
 * Maven Deploy → Nexus
 *   ↓
 * Docker Build
 *   ↓
 * Docker Login
 *   ↓
 * Docker Push → Nexus Registry
 *   ↓
 * Docker Manifest Verify
 *   ↓
 * K3s Rolling Deployment
 *   ↓
 * Rollout Status
 *   ↓
 * Verify Running Image
 *   ↓
 * Success
 *
 * ============================================================
 *
 * Maven:
 *
 * SNAPSHOT:
 *   0.0.2-SNAPSHOT
 *   →
 *   nexus/maven-snapshots
 *
 * RELEASE:
 *   0.0.2
 *   →
 *   nexus/maven-releases
 *
 * Docker:
 *
 *   192.168.0.103:8082/backend-springboot:
 *       0.0.2-SNAPSHOT-build-46
 *
 * K3s:
 *
 *   namespace: backend
 *   deployment: backend-springboot
 *   container: backend-springboot
 *
 * ============================================================
 */


// ============================================================
// GITHUB
// ============================================================

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

def k3sKubeconfig =
    '/home/jenkins/k3s-jenkins.yaml'

def k3sNamespace =
    'backend'

def k3sDeployment =
    'backend-springboot'

def k3sContainer =
    'backend-springboot'


// ============================================================
// APPLICATION VARIABLES
// ============================================================

def appName = null
def appVersion = null
def dockerTag = null
def dockerImage = null

def gitCommitId = null

def groupId = null
def artifactId = null

def isSnapshot = false

def previousImage = null


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

        "KUBECONFIG=${k3sKubeconfig}",

        "K3S_NAMESPACE=${k3sNamespace}",

        "K3S_DEPLOYMENT=${k3sDeployment}",

        "K3S_CONTAINER=${k3sContainer}"

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

                    returnStdout: true,

                    script: '''
                        set -e

                        git rev-parse HEAD
                    '''

                ).trim()


                echo "Git repository : ${gitRepo}"

                echo "Git branch     : ${gitBranch}"

                echo "Git commit     : ${gitCommitId}"
            }


            // =================================================
            // 02 - PREPARE NEXUS
            // =================================================

            stage('02 - Prepare Nexus') {

                echo '========================================'

                echo 'PREPARE MAVEN / NEXUS'

                echo '========================================'


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

                withCredentials([

                    usernamePassword(

                        credentialsId: 'nexus-credential',

                        usernameVariable: 'NEXUS_USERNAME',

                        passwordVariable: 'NEXUS_PASSWORD'
                    )

                ]) {

                    def pomVersion =
                        getFromPom('version')


                    groupId =
                        getFromPom('groupId')


                    artifactId =
                        getFromPom('artifactId')


                    appName =
                        artifactId


                    echo '========================================'

                    echo 'APPLICATION INFORMATION'

                    echo '========================================'

                    echo "GroupId      : ${groupId}"

                    echo "ArtifactId   : ${artifactId}"

                    echo "POM Version  : ${pomVersion}"

                    echo "Git Commit   : ${gitCommitId}"


                    // -----------------------------------------
                    // SNAPSHOT
                    // -----------------------------------------

                    if (
                        pomVersion.endsWith('-SNAPSHOT')
                    ) {

                        isSnapshot = true

                        appVersion =
                            pomVersion

                        echo 'Build Type   : SNAPSHOT'

                    }

                    // -----------------------------------------
                    // RELEASE
                    // -----------------------------------------

                    else {

                        isSnapshot = false


                        appVersion =
                            getNextReleaseVersion(

                                nexusReleaseRepo,

                                groupId,

                                artifactId
                            )


                        echo 'Build Type   : RELEASE'

                        echo "New Version  : ${appVersion}"


                        sh """

                            set -e

                            export PATH="\\$JAVA_HOME/bin:\\$MAVEN_HOME/bin:\\$PATH"


                            mvn \\

                                -s settings.xml \\

                                versions:set \\

                                -DnewVersion=${appVersion} \\

                                -DgenerateBackupPoms=false

                        """


                        def verifiedVersion =
                            getFromPom('version')


                        if (
                            verifiedVersion != appVersion
                        ) {

                            error(

                                "Version mismatch: " +

                                "expected=${appVersion}, " +

                                "actual=${verifiedVersion}"
                            )
                        }
                    }


                    // -----------------------------------------
                    // DOCKER TAG
                    // -----------------------------------------

                    dockerTag =
                        "${appVersion}-build-${BUILD_NUMBER}"


                    dockerImage =
                        "${nexusDockerRegistry}/${appName}:${dockerTag}"


                    echo '----------------------------------------'

                    echo "Application : ${appName}"

                    echo "Version     : ${appVersion}"

                    echo "Snapshot    : ${isSnapshot}"

                    echo "Docker Tag  : ${dockerTag}"

                    echo "Docker Image: ${dockerImage}"

                    echo "Build       : ${BUILD_NUMBER}"

                    echo "Commit      : ${gitCommitId}"

                    echo '----------------------------------------'
                }
            }


            // =================================================
            // 04 - MAVEN BUILD & TEST
            // =================================================

            stage('04 - Maven Build & Test') {

                echo '========================================'

                echo 'MAVEN BUILD & TEST'

                echo '========================================'


                sh '''

                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"


                    java -version

                    mvn -version


                    mvn \\
                        -s settings.xml \\
                        clean \\
                        verify
                '''
            }


            // =================================================
            // 05 - SONARQUBE
            // =================================================

            stage('05 - SonarQube') {

                echo '========================================'

                echo 'SONARQUBE ANALYSIS'

                echo '========================================'


                withSonarQubeEnv('SonarQube') {

                    sh """

                        set -e

                        export PATH="\\$JAVA_HOME/bin:\\$MAVEN_HOME/bin:\\$PATH"


                        mvn \\

                            -s settings.xml \\

                            org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar \\

                            -Dsonar.projectKey=${appName} \\

                            -Dsonar.projectName=${appName} \\

                            -Dsonar.projectVersion=${appVersion} \\

                            -Dsonar.scm.revision=${gitCommitId}

                    """
                }
            }


            // =================================================
            // 06 - QUALITY GATE
            // =================================================

            stage('06 - Quality Gate') {

                echo '========================================'

                echo 'WAITING FOR SONARQUBE QUALITY GATE'

                echo '========================================'


                timeout(

                    time: 10,

                    unit: 'MINUTES'

                ) {

                    def qualityGate =

                        waitForQualityGate(

                            abortPipeline: true

                        )


                    echo "Quality Gate : ${qualityGate.status}"


                    if (

                        qualityGate.status != 'OK'

                    ) {

                        error(

                            "SonarQube Quality Gate FAILED: " +

                            qualityGate.status

                        )
                    }


                    echo 'SonarQube Quality Gate PASSED'
                }
            }


            // =================================================
            // 07 - MAVEN DEPLOY
            // =================================================

            stage('07 - Deploy Maven to Nexus') {

                echo '========================================'

                echo 'DEPLOY MAVEN ARTIFACT TO NEXUS'

                echo '========================================'


                echo "Application : ${appName}"

                echo "Version     : ${appVersion}"

                echo "Snapshot    : ${isSnapshot}"


                sh '''

                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"


                    mvn \\
                        -s settings.xml \\
                        deploy \\
                        -DskipTests
                '''


                echo 'Maven artifact successfully deployed.'
            }


            // =================================================
            // 08 - DOCKER CHECK
            // =================================================

            stage('08 - Docker Check') {

                echo '========================================'

                echo 'DOCKER CHECK'

                echo '========================================'


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

                echo '========================================'

                echo 'DOCKER BUILD'

                echo '========================================'


                echo "Image : ${dockerImage}"


                sh """

                    set -e


                    test -f Dockerfile


                    docker build \\

                        --pull \\

                        -t ${dockerImage} \\

                        .


                    echo ''

                    echo 'Docker image successfully created.'

                    echo "Image: ${dockerImage}"

                """
            }


            // =================================================
            // 10 - DOCKER LOGIN
            // =================================================

            stage('10 - Docker Login') {

                echo '========================================'

                echo 'LOGIN TO NEXUS DOCKER REGISTRY'

                echo '========================================'


                withCredentials([

                    usernamePassword(

                        credentialsId: 'nexus-credential',

                        usernameVariable: 'NEXUS_USERNAME',

                        passwordVariable: 'NEXUS_PASSWORD'
                    )

                ]) {

                    sh """

                        set -e


                        echo "Logging in to ${nexusDockerRegistry}..."


                        echo "\\$NEXUS_PASSWORD" |

                            docker login \\

                                ${nexusDockerRegistry} \\

                                --username "\\$NEXUS_USERNAME" \\

                                --password-stdin


                        echo ''

                        echo 'Docker login successful.'

                    """
                }
            }


            // =================================================
            // 11 - DOCKER PUSH
            // =================================================

            stage('11 - Docker Push to Nexus') {

                echo '========================================'

                echo 'PUSH DOCKER IMAGE TO NEXUS'

                echo '========================================'


                echo "Registry : ${nexusDockerRegistry}"

                echo "Image    : ${dockerImage}"


                sh """

                    set -e


                    echo 'Pushing Docker image...'


                    docker push ${dockerImage}


                    echo ''

                    echo 'Docker push successful.'

                    echo "Image: ${dockerImage}"

                """
            }


            // =================================================
            // 12 - VERIFY DOCKER MANIFEST
            // =================================================

            stage('12 - Verify Docker Manifest') {

                echo '========================================'

                echo 'VERIFY DOCKER MANIFEST'

                echo '========================================'


                withCredentials([

                    usernamePassword(

                        credentialsId: 'nexus-credential',

                        usernameVariable: 'NEXUS_USERNAME',

                        passwordVariable: 'NEXUS_PASSWORD'
                    )

                ]) {

                    sh """

                        set -e


                        echo 'Logging in...'


                        echo "\\$NEXUS_PASSWORD" |

                            docker login \\

                                ${nexusDockerRegistry} \\

                                --username "\\$NEXUS_USERNAME" \\

                                --password-stdin


                        echo ''

                        echo 'Checking Docker manifest...'


                        docker manifest inspect \\

                            ${dockerImage}


                        echo ''

                        echo 'Docker manifest verified successfully.'

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


                    test -f ${k3sKubeconfig}


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        version


                    echo ''

                    echo 'K3s Nodes:'


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        get nodes -o wide


                    echo ''

                    echo 'K3s Backend Namespace:'


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        get pods \\

                        -n ${k3sNamespace}

                """
            }


            // =================================================
            // 14 - VERIFY K3S DEPLOYMENT
            // =================================================

            stage('14 - Verify K3s Deployment') {

                echo '========================================'

                echo 'VERIFY K3S DEPLOYMENT'

                echo '========================================'


                sh """

                    set -e


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        -n ${k3sNamespace} \\

                        get deployment \\

                        ${k3sDeployment}


                    echo ''

                    echo 'Current deployment image:'


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        -n ${k3sNamespace} \\

                        get deployment \\

                        ${k3sDeployment} \\

                        -o jsonpath='{.spec.template.spec.containers[?(@.name=="${k3sContainer}")].image}'


                    echo ''

                """
            }


            // =================================================
            // 15 - SAVE PREVIOUS IMAGE
            // =================================================

            stage('15 - Save Previous Image') {

                echo '========================================'

                echo 'SAVE CURRENT K3S IMAGE'

                echo '========================================'


                previousImage = sh(

                    returnStdout: true,

                    script: """

                        set -e


                        kubectl \\

                            --kubeconfig=${k3sKubeconfig} \\

                            -n ${k3sNamespace} \\

                            get deployment \\

                            ${k3sDeployment} \\

                            -o jsonpath='{.spec.template.spec.containers[?(@.name=="${k3sContainer}")].image}'

                    """

                ).trim()


                echo "Previous image: ${previousImage}"
            }


            // =================================================
            // 16 - K3S ROLLING DEPLOYMENT
            // =================================================

            stage('16 - K3s Rolling Deployment') {

                echo '========================================'

                echo 'K3S ROLLING DEPLOYMENT'

                echo '========================================'


                echo "New image: ${dockerImage}"


                sh """

                    set -e


                    echo 'Updating deployment image...'


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        -n ${k3sNamespace} \\

                        set image deployment/${k3sDeployment} \\

                        ${k3sContainer}=${dockerImage}


                    echo ''

                    echo 'Image update completed.'


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        -n ${k3sNamespace} \\

                        rollout restart deployment/${k3sDeployment}


                    echo ''

                    echo 'Rolling deployment started.'

                """
            }


            // =================================================
            // 17 - ROLLOUT STATUS
            // =================================================

            stage('17 - K3s Rollout Status') {

                echo '========================================'

                echo 'WAIT FOR K3S ROLLOUT'

                echo '========================================'


                try {

                    sh """

                        set -e


                        kubectl \\

                            --kubeconfig=${k3sKubeconfig} \\

                            -n ${k3sNamespace} \\

                            rollout status \\

                            deployment/${k3sDeployment} \\

                            --timeout=300s

                    """

                    echo 'K3s rollout completed successfully.'

                }

                catch (Exception rolloutError) {

                    echo '========================================'

                    echo 'K3S ROLLOUT FAILED'

                    echo '========================================'


                    sh """

                        kubectl \\

                            --kubeconfig=${k3sKubeconfig} \\

                            -n ${k3sNamespace} \\

                            get pods -o wide \\

                            || true


                        echo ''

                        echo 'Deployment description:'


                        kubectl \\

                            --kubeconfig=${k3sKubeconfig} \\

                            -n ${k3sNamespace} \\

                            describe deployment \\

                            ${k3sDeployment} \\

                            || true


                        echo ''

                        echo 'Recent events:'


                        kubectl \\

                            --kubeconfig=${k3sKubeconfig} \\

                            -n ${k3sNamespace} \\

                            get events \\

                            --sort-by='.lastTimestamp' \\

                            | tail -50 \\

                            || true

                    """


                    throw rolloutError
                }
            }


            // =================================================
            // 18 - VERIFY RUNNING PODS
            // =================================================

            stage('18 - Verify Running Pods') {

                echo '========================================'

                echo 'VERIFY RUNNING PODS'

                echo '========================================'


                sh """

                    set -e


                    echo 'Pods:'


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        -n ${k3sNamespace} \\

                        get pods -o wide


                    echo ''

                    echo 'Deployment:'


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        -n ${k3sNamespace} \\

                        get deployment \\

                        ${k3sDeployment}


                    echo ''

                    echo 'Running image:'


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        -n ${k3sNamespace} \\

                        get deployment \\

                        ${k3sDeployment} \\

                        -o jsonpath='{.spec.template.spec.containers[?(@.name=="${k3sContainer}")].image}'


                    echo ''

                """
            }


            // =================================================
            // 19 - VERIFY IMAGE
            // =================================================

            stage('19 - Verify Deployed Image') {

                echo '========================================'

                echo 'VERIFY DEPLOYED IMAGE'

                echo '========================================'


                def deployedImage = sh(

                    returnStdout: true,

                    script: """

                        kubectl \\

                            --kubeconfig=${k3sKubeconfig} \\

                            -n ${k3sNamespace} \\

                            get deployment \\

                            ${k3sDeployment} \\

                            -o jsonpath='{.spec.template.spec.containers[?(@.name=="${k3sContainer}")].image}'

                    """

                ).trim()


                echo "Expected image : ${dockerImage}"

                echo "Deployed image : ${deployedImage}"


                if (

                    deployedImage != dockerImage

                ) {

                    error(

                        "K3s image mismatch. " +

                        "Expected=${dockerImage}, " +

                        "Actual=${deployedImage}"

                    )
                }


                echo 'K3s image verification PASSED.'
            }


            // =================================================
            // 20 - FINAL K3S STATUS
            // =================================================

            stage('20 - Final K3s Status') {

                echo '========================================'

                echo 'FINAL K3S STATUS'

                echo '========================================'


                sh """

                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        -n ${k3sNamespace} \\

                        get pods -o wide


                    echo ''


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        -n ${k3sNamespace} \\

                        get svc


                    echo ''


                    kubectl \\

                        --kubeconfig=${k3sKubeconfig} \\

                        -n ${k3sNamespace} \\

                        get ingress

                """
            }


            // =================================================
            // SUCCESS
            // =================================================

            echo ''

            echo '========================================'

            echo 'PIPELINE SUCCESS'

            echo '========================================'

            echo "Application      : ${appName}"

            echo "Version          : ${appVersion}"

            echo "Docker Tag       : ${dockerTag}"

            echo "Docker Image     : ${dockerImage}"

            echo "Git Commit       : ${gitCommitId}"

            echo "Build Number     : ${BUILD_NUMBER}"

            echo "Maven Repository : Nexus"

            echo "Docker Registry  : ${nexusDockerRegistry}"

            echo "K3s Namespace    : ${k3sNamespace}"

            echo "K3s Deployment   : ${k3sDeployment}"

            echo 'Deployment       : SUCCESS'

            echo '========================================'


        }

        catch (Exception e) {

            // =================================================
            // PIPELINE FAILED
            // =================================================

            echo ''

            echo '========================================'

            echo 'PIPELINE FAILED'

            echo '========================================'

            echo "Build #${BUILD_NUMBER} FAILED."

            echo ''

            echo 'Original pipeline error:'

            echo "${e}"

            echo '========================================'


            // =================================================
            // K3S ROLLBACK
            // =================================================

            if (

                previousImage != null &&

                previousImage.trim() != '' &&

                previousImage != dockerImage

            ) {

                echo ''

                echo '========================================'

                echo 'ATTEMPTING K3S ROLLBACK'

                echo '========================================'


                try {

                    sh """

                        set -e


                        echo 'Rollback image:'

                        echo '${previousImage}'


                        kubectl \\

                            --kubeconfig=${k3sKubeconfig} \\

                            -n ${k3sNamespace} \\

                            set image deployment/${k3sDeployment} \\

                            ${k3sContainer}=${previousImage}


                        echo ''

                        echo 'Waiting for rollback...'


                        kubectl \\

                            --kubeconfig=${k3sKubeconfig} \\

                            -n ${k3sNamespace} \\

                            rollout status \\

                            deployment/${k3sDeployment} \\

                            --timeout=300s


                        echo ''

                        echo 'Rollback successful.'


                        echo ''

                        echo 'Current deployment image:'


                        kubectl \\

                            --kubeconfig=${k3sKubeconfig} \\

                            -n ${k3sNamespace} \\

                            get deployment \\

                            ${k3sDeployment} \\

                            -o jsonpath='{.spec.template.spec.containers[?(@.name=="${k3sContainer}")].image}'


                        echo ''

                    """

                }

                catch (Exception rollbackError) {

                    echo ''

                    echo '========================================'

                    echo 'K3S ROLLBACK FAILED'

                    echo '========================================'

                    echo "${rollbackError}"

                    echo '========================================'
                }

            }


            throw e
        }

        finally {

            // =================================================
            // DOCKER CLEANUP
            // =================================================

            sh """

                if [ -n "${dockerImage ?: ''}" ]; then

                    echo 'Cleaning Docker image...'


                    docker image rm \\

                        "${dockerImage}" \\

                        || true

                fi

            """


            // =================================================
            // DOCKER LOGOUT
            // =================================================

            sh """

                docker logout ${nexusDockerRegistry} \\

                    || true

            """


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

            export PATH="\\$JAVA_HOME/bin:\\$MAVEN_HOME/bin:\\$PATH"


            mvn \\

                -s settings.xml \\

                -q \\

                org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate \\

                -Dexpression=project.${key} \\

                -DforceStdout \\

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

                curl \\

                    -fsS \\

                    -u "\\$NEXUS_USERNAME:\\$NEXUS_PASSWORD" \\

                    "${metadataUrl}" \\

                    || true

            """

        ).trim()


        if (

            !metadata ||

            !metadata.contains('<version>')

        ) {

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


        if (

            versions.isEmpty()

        ) {

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

    def pom =
        'pom.xml'


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


    if (

        projectEnd == -1

    ) {

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

    sh """

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

            <username>\\${NEXUS_USERNAME}</username>

            <password>\\${NEXUS_PASSWORD}</password>

        </server>


        <server>

            <id>nexus-snapshots</id>

            <username>\\${NEXUS_USERNAME}</username>

            <password>\\${NEXUS_PASSWORD}</password>

        </server>


        <server>

            <id>nexus-public</id>

            <username>\\${NEXUS_USERNAME}</username>

            <password>\\${NEXUS_PASSWORD}</password>

        </server>


    </servers>


    <mirrors>


        <mirror>

            <id>nexus-public</id>

            <name>Nexus Public Repository</name>

            <url>${nexusPublicRepo}</url>

            <mirrorOf>*</mirrorOf>

        </mirror>


    </mirrors>


</settings>

EOF


        chmod 600 settings.xml


        echo 'settings.xml created.'

    """
}