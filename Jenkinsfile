/*
 * ============================================================
 * SPRING BOOT CI/CD
 *
 * GitHub → Maven Build/Test → SonarQube → Nexus
 *
 * RELEASE:
 *   0.0.1 → 0.0.2 → 0.0.3 → ...
 *
 * SNAPSHOT:
 *   0.0.x-SNAPSHOT
 *
 * ============================================================
 */

// ------------------------------------------------------------
// CONFIGURATION
// ------------------------------------------------------------

def gitRepo = 'https://github.com/taqin21in/backend-springboot.git'
def gitBranch = 'main'

def nexusBaseUrl = 'http://192.168.0.103:8081'
def nexusPublicRepo = "${nexusBaseUrl}/repository/maven-public/"
def nexusReleaseRepo = "${nexusBaseUrl}/repository/maven-releases/"
def nexusSnapshotRepo = "${nexusBaseUrl}/repository/maven-snapshots/"

def appName
def appVersion
def gitCommitId
def isSnapshot = false


node('runner') {

    // --------------------------------------------------------
    // JENKINS BUILD PROTECTION
    // --------------------------------------------------------

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
            // 1. CHECKOUT
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

                echo "Git commit: ${gitCommitId}"
            }


            // =================================================
            // 2. PREPARE NEXUS + DETERMINE VERSION
            // =================================================

            stage('Prepare & Determine Version') {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-credential',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {

                    // -----------------------------------------
                    // Create Maven settings.xml
                    // -----------------------------------------

                    prepareSettingsXml(
                        nexusPublicRepo
                    )

                    // -----------------------------------------
                    // Add Nexus distribution management
                    // -----------------------------------------

                    addDistributionToPom(
                        nexusReleaseRepo,
                        nexusSnapshotRepo
                    )

                    // -----------------------------------------
                    // Read POM
                    // -----------------------------------------

                    def pomVersion = getFromPom('version')
                    def groupId = getFromPom('groupId')
                    def artifactId = getFromPom('artifactId')

                    appName = artifactId

                    echo "Application : ${appName}"
                    echo "GroupId     : ${groupId}"
                    echo "POM Version : ${pomVersion}"


                    // -----------------------------------------
                    // SNAPSHOT
                    // -----------------------------------------

                    if (pomVersion.endsWith('-SNAPSHOT')) {

                        isSnapshot = true
                        appVersion = pomVersion

                        echo "SNAPSHOT build detected."
                    }


                    // -----------------------------------------
                    // RELEASE
                    // -----------------------------------------

                    else {

                        isSnapshot = false

                        appVersion = getNextReleaseVersion(
                            nexusReleaseRepo,
                            groupId,
                            artifactId
                        )

                        echo "Next RELEASE version: ${appVersion}"

                        // Update POM version
                        sh """
                            export PATH="\$JAVA_HOME/bin:\$MAVEN_HOME/bin:\$PATH"

                            mvn \
                                -s settings.xml \
                                versions:set \
                                -DnewVersion=${appVersion} \
                                versions:commit \
                                -DgenerateBackupPoms=false
                        """

                        def verifiedVersion = getFromPom('version')

                        if (verifiedVersion != appVersion) {
                            error(
                                "Version mismatch: " +
                                "expected=${appVersion}, " +
                                "actual=${verifiedVersion}"
                            )
                        }
                    }

                    echo "----------------------------------------"
                    echo "Application : ${appName}"
                    echo "Version     : ${appVersion}"
                    echo "Snapshot    : ${isSnapshot}"
                    echo "Commit      : ${gitCommitId}"
                    echo "Build       : ${BUILD_NUMBER}"
                    echo "----------------------------------------"
                }
            }


            // =================================================
            // 3. BUILD + UNIT TEST + INTEGRATION TEST
            // =================================================

            stage('Build & Test') {

                sh '''
                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                    mvn \
                        clean \
                        verify \
                        -s settings.xml
                '''
            }


            // =================================================
            // 4. SONARQUBE
            // =================================================

            stage('SonarQube') {

                withSonarQubeEnv('SonarQube') {

                    sh """
                        set -e

                        export PATH="\$JAVA_HOME/bin:\$MAVEN_HOME/bin:\$PATH"

                        mvn \
                            org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar \
                            -s settings.xml \
                            -Dsonar.projectKey=${appName} \
                            -Dsonar.projectName=${appName} \
                            -Dsonar.projectVersion=${appVersion} \
                            -Dsonar.scm.revision=${gitCommitId}
                    """
                }
            }


            // =================================================
            // 5. SONARQUBE QUALITY GATE
            // =================================================

            stage('Quality Gate') {

                timeout(
                    time: 10,
                    unit: 'MINUTES'
                ) {

                    def qualityGate = waitForQualityGate(
                        abortPipeline: false
                    )

                    echo "SonarQube Quality Gate: ${qualityGate.status}"

                    if (qualityGate.status != 'OK') {

                        error(
                            "SonarQube Quality Gate FAILED: " +
                            qualityGate.status
                        )
                    }
                }
            }


            // =================================================
            // 6. DEPLOY TO NEXUS
            // =================================================

            stage('Deploy Nexus') {

                echo "----------------------------------------"
                echo "Deploying artifact to Nexus"
                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
                echo "Snapshot    : ${isSnapshot}"
                echo "----------------------------------------"

                sh '''
                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                    mvn \
                        deploy \
                        -DskipTests \
                        -s settings.xml
                '''
            }


            // =================================================
            // DONE
            // =================================================

            echo "========================================"
            echo "PIPELINE SUCCESS"
            echo "========================================"
            echo "Application : ${appName}"
            echo "Version     : ${appVersion}"
            echo "Commit      : ${gitCommitId}"
            echo "Build       : ${BUILD_NUMBER}"
            echo "========================================"


        } catch (Exception e) {

            echo "========================================"
            echo "PIPELINE FAILED"
            echo "========================================"
            echo "Build #${BUILD_NUMBER} FAILED"
            echo "========================================"

            throw e

        } finally {

            // Remove temporary Maven credentials
            sh '''
                rm -f settings.xml || true
            '''

            deleteDir()

            echo "Workspace cleanup completed."
        }
    }
}


// ============================================================
// GET VALUE FROM POM
// ============================================================

def getFromPom(key) {

    return sh(
        returnStdout: true,

        script: """
            export PATH="\$JAVA_HOME/bin:\$MAVEN_HOME/bin:\$PATH"

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
// GET NEXT RELEASE VERSION FROM NEXUS
// ============================================================

def getNextReleaseVersion(
    nexusReleaseRepo,
    groupId,
    artifactId
) {

    def groupPath = groupId.replace('.', '/')

    def metadataUrl =
        "${nexusReleaseRepo}" +
        "${groupPath}/" +
        "${artifactId}/" +
        "maven-metadata.xml"


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
                    -u "\$NEXUS_USERNAME:\$NEXUS_PASSWORD" \
                    "${metadataUrl}" \
                    || true
            """
        ).trim()


        // -----------------------------------------------
        // No release exists
        // -----------------------------------------------

        if (
            !metadata ||
            !metadata.contains('<version>')
        ) {

            echo "No existing release found."
            echo "Next version: 0.0.1"

            return '0.0.1'
        }


        // -----------------------------------------------
        // Extract versions
        // -----------------------------------------------

        def versions = []

        def matcher =
            metadata =~ /<version>([^<]+)<\/version>/


        matcher.each {

            def version = it[1].trim()

            if (
                version ==~ /^\d+\.\d+\.\d+$/
            ) {
                versions << version
            }
        }


        if (versions.isEmpty()) {

            echo "No valid release version found."
            return '0.0.1'
        }


        // -----------------------------------------------
        // Find highest semantic version
        // -----------------------------------------------

        def maxVersion = versions.max { a, b ->

            def pa = a.tokenize('.').collect {
                it as Integer
            }

            def pb = b.tokenize('.').collect {
                it as Integer
            }

            if (pa[0] != pb[0]) {
                return pa[0] <=> pb[0]
            }

            if (pa[1] != pb[1]) {
                return pa[1] <=> pb[1]
            }

            return pa[2] <=> pb[2]
        }


        // -----------------------------------------------
        // Increment PATCH version
        // -----------------------------------------------

        def parts = maxVersion.tokenize('.').collect {
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
// ADD DISTRIBUTION MANAGEMENT TO POM
// ============================================================

def addDistributionToPom(
    nexusReleaseRepo,
    nexusSnapshotRepo
) {

    def pom = 'pom.xml'
    def content = readFile(pom)


    // Already configured
    if (content.contains('<distributionManagement>')) {

        echo "distributionManagement already exists."
        return
    }


    def distributionManagement = """
    <distributionManagement>

        <repository>
            <id>nexus-releases</id>
            <name>Internal Nexus Releases</name>
            <url>${nexusReleaseRepo}</url>
        </repository>

        <snapshotRepository>
            <id>nexus-snapshots</id>
            <name>Internal Nexus Snapshots</name>
            <url>${nexusSnapshotRepo}</url>
        </snapshotRepository>

    </distributionManagement>
    """


    def projectEnd = content.lastIndexOf('</project>')

    if (projectEnd == -1) {

        error(
            'Invalid pom.xml: </project> not found'
        )
    }


    def newContent =
        content.substring(0, projectEnd) +
        distributionManagement +
        content.substring(projectEnd)


    writeFile(
        file: pom,
        text: newContent
    )

    echo "Nexus distributionManagement added."
}


// ============================================================
// CREATE SETTINGS.XML
// ============================================================

def prepareSettingsXml(nexusPublicRepo) {

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