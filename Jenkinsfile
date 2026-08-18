/*
 * ============================================================
 * SPRING BOOT CI/CD
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
 * Nexus Docker Registry
 *
 * Maven:
 *   SNAPSHOT -> maven-snapshots
 *   RELEASE  -> maven-releases
 *
 * Docker:
 *   192.168.0.103:8082/backend-springboot:<version>
 *
 * ============================================================
 */


// ============================================================
// CONFIGURATION
// ============================================================

def gitRepo   = 'https://github.com/taqin21in/backend-springboot.git'
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
//
// IMPORTANT:
// Registry 8082 currently uses HTTP.
// Therefore Docker daemon must allow it as insecure registry.
// ============================================================

def nexusDockerRegistry = '192.168.0.103:8082'


// ============================================================
// VARIABLES
// ============================================================

def appName
def appVersion
def gitCommitId
def dockerImage

def isSnapshot = false


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

        'MAVEN_HOME=/opt/maven'

    ]) {

        try {

            // =================================================
            // 1. CHECKOUT
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
                        git rev-parse HEAD
                    ''',

                    returnStdout: true

                ).trim()


                echo "Git commit : ${gitCommitId}"
            }


            // =================================================
            // 2. PREPARE NEXUS
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
            // 3. DETERMINE VERSION
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


                    // -----------------------------------------
                    // SNAPSHOT
                    // -----------------------------------------

                    if (
                        pomVersion.endsWith('-SNAPSHOT')
                    ) {

                        isSnapshot = true

                        appVersion = pomVersion

                        echo "Build Type  : SNAPSHOT"
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


                        echo "Build Type  : RELEASE"
                        echo "New Version : ${appVersion}"


                        sh """

                            set -e

                            export PATH="\$JAVA_HOME/bin:\$MAVEN_HOME/bin:\$PATH"

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


                    echo '----------------------------------------'
                    echo "Application : ${appName}"
                    echo "Version     : ${appVersion}"
                    echo "Snapshot    : ${isSnapshot}"
                    echo "Commit      : ${gitCommitId}"
                    echo "Build       : ${BUILD_NUMBER}"
                    echo '----------------------------------------'
                }
            }


            // =================================================
            // 4. MAVEN BUILD
            // =================================================

            stage('04 - Maven Build & Test') {

                sh '''

                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                    echo "========================================"
                    echo "MAVEN BUILD"
                    echo "========================================"

                    mvn \
                        -s settings.xml \
                        clean \
                        verify

                '''
            }


            // =================================================
            // 5. SONARQUBE
            // =================================================

            stage('05 - SonarQube') {

                withSonarQubeEnv('SonarQube') {

                    sh """

                        set -e

                        export PATH="\$JAVA_HOME/bin:\$MAVEN_HOME/bin:\$PATH"

                        echo "========================================"
                        echo "SONARQUBE ANALYSIS"
                        echo "========================================"

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
            // 6. QUALITY GATE
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
            // 7. MAVEN DEPLOY
            // =================================================

            stage('07 - Deploy Maven to Nexus') {

                echo '========================================'
                echo 'DEPLOY MAVEN ARTIFACT'
                echo '========================================'

                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
                echo "Snapshot    : ${isSnapshot}"


                sh '''

                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                    mvn \
                        -s settings.xml \
                        deploy \
                        -DskipTests

                '''
            }


            // =================================================
            // 8. DOCKER CHECK
            // =================================================

            stage('08 - Docker Check') {

                sh '''

                    set -e

                    echo "========================================"
                    echo "DOCKER CHECK"
                    echo "========================================"

                    docker --version

                    echo ""

                    docker info

                '''
            }


            // =================================================
            // 9. DOCKER BUILD
            // =================================================

            stage('09 - Docker Build') {

                dockerImage =
                    "${nexusDockerRegistry}/${appName}:${appVersion}"


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


                    echo ""
                    echo "Docker image created:"
                    echo "${dockerImage}"

                """
            }


            // =================================================
            // 10. DOCKER PUSH
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
                echo 'DOCKER PUSH TO NEXUS'
                echo '========================================'

                echo "Registry : ${nexusDockerRegistry}"
                echo "Image    : ${dockerImage}"


                sh """

                set -e

                echo "Logging in to Nexus Docker Registry..."

                echo "\$NEXUS_PASSWORD" | \\
                    docker login \\
                    ${nexusDockerRegistry} \\
                    --username "\$NEXUS_USERNAME" \\
                    --password-stdin


                echo "Docker login successful."


                echo "Pushing image..."

                docker push ${dockerImage}


                echo "Docker push successful."


                docker logout ${nexusDockerRegistry} || true

                """
                }
            }


            // =================================================
            // 11. CLEAN IMAGE
            // =================================================

            stage('11 - Docker Cleanup') {

                sh """

                    docker image rm \\
                        ${dockerImage} \\
                        || true

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
            echo "Commit      : ${gitCommitId}"
            echo "Build       : ${BUILD_NUMBER}"
            echo "Maven       : Nexus"
            echo "Docker      : ${dockerImage}"
            echo '========================================'


        } catch (Exception e) {

            echo ''
            echo '========================================'
            echo 'PIPELINE FAILED'
            echo '========================================'
            echo "Build #${BUILD_NUMBER} FAILED"
            echo '========================================'

            throw e

        } finally {

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

    return sh(

        returnStdout: true,

        script: """

            export PATH="\$JAVA_HOME/bin:\$MAVEN_HOME/bin:\$PATH"

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
                    -u "\$NEXUS_USERNAME:\$NEXUS_PASSWORD" \\
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
                version ==~ /^\d+\.\d+\.\d+$/
            ) {

                versions << version
            }
        }


        if (versions.isEmpty()) {

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

            <name>Nexus Public Repository</name>

            <url>${nexusPublicRepo}</url>

            <mirrorOf>*</mirrorOf>

        </mirror>

    </mirrors>


</settings>

EOF


        chmod 600 settings.xml


        echo "settings.xml created."

    """
}