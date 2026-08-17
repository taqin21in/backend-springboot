def git_repo = 'https://github.com/taqin21in/backend-springboot.git'
def git_branch = 'main'

def nexus_base_url = 'http://192.168.0.103:8081'

def nexus_deps_repo =
    "${nexus_base_url}/repository/maven-public/"

def nexus_release_repo =
    "${nexus_base_url}/repository/maven-releases/"

def nexus_snapshot_repo =
    "${nexus_base_url}/repository/maven-snapshots/"

def appName
def appVersion
def gitCommitId
def isSnapshot = false

node('runner') {

    /*
     * ========================================================
     * JENKINS BUILD PROTECTION
     * ========================================================
     */

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


    /*
     * ========================================================
     * JAVA + MAVEN
     * ========================================================
     */

    withEnv([
        'JAVA_HOME=/usr/lib/jvm/java-21-openjdk-21.0.12.0.8-1.2.el9_8.x86_64',
        'MAVEN_HOME=/opt/maven'
    ]) {

        try {

            /*
             * ====================================================
             * ENVIRONMENT
             * ====================================================
             */

            stage('Environment Check') {

                sh '''
                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                    echo "========================================"
                    echo "ENVIRONMENT"
                    echo "========================================"

                    echo "Hostname:"
                    hostname

                    echo ""
                    echo "User:"
                    whoami

                    echo ""
                    echo "Workspace:"
                    pwd

                    echo ""
                    echo "JAVA:"
                    java -version

                    echo ""
                    echo "MAVEN:"
                    mvn -version

                    echo ""
                    echo "GIT:"
                    git --version
                '''
            }


            /*
             * ====================================================
             * CHECKOUT
             * ====================================================
             */

            stage('Checkout') {

                deleteDir()

                git(
                    url: git_repo,
                    branch: git_branch,
                    credentialsId: 'github-credential'
                )

                sh '''
                    set -e

                    echo "========================================"
                    echo "GIT INFORMATION"
                    echo "========================================"

                    echo "Commit:"
                    git rev-parse HEAD

                    echo ""
                    echo "Branch:"
                    git branch --show-current

                    echo ""
                    echo "Latest tag:"
                    git describe --tags --always || true
                '''
            }


            /*
             * ====================================================
             * MAVEN PROJECT
             * ====================================================
             */

            stage('Check Maven Project') {

                sh '''
                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                    if [ ! -f pom.xml ]; then
                        echo "ERROR: pom.xml tidak ditemukan!"
                        exit 1
                    fi

                    echo "========================================"
                    echo "MAVEN PROJECT"
                    echo "========================================"

                    echo ""
                    echo "GroupId:"

                    mvn help:evaluate \
                        -Dexpression=project.groupId \
                        -q \
                        -DforceStdout

                    echo ""
                    echo "ArtifactId:"

                    mvn help:evaluate \
                        -Dexpression=project.artifactId \
                        -q \
                        -DforceStdout

                    echo ""
                    echo "Version:"

                    mvn help:evaluate \
                        -Dexpression=project.version \
                        -q \
                        -DforceStdout
                '''
            }


            /*
             * ====================================================
             * PREPARE NEXUS
             * ====================================================
             */

            stage('Prepare Nexus') {

                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-credential',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {

                    prepareSettingsXml(
                        nexus_deps_repo
                    )

                    addDistributionToPom(
                        nexus_release_repo,
                        nexus_snapshot_repo
                    )
                }
            }


            /*
             * ====================================================
             * DETERMINE VERSION
             *
             * Release:
             *
             * 0.0.1
             * 0.0.2
             * 0.0.3
             *
             * Snapshot:
             *
             * 0.0.1-SNAPSHOT
             *
             * ====================================================
             */

            stage('Determine Version') {

                def pomVersion = getFromPom('version')

                def groupId = getFromPom('groupId')
                def artifactId = getFromPom('artifactId')

                appName = artifactId

                gitCommitId =
                    sh(
                        returnStdout: true,
                        script: 'git rev-parse HEAD'
                    ).trim()

                echo "POM Version : ${pomVersion}"


                /*
                 * ------------------------------------------------
                 * SNAPSHOT
                 * ------------------------------------------------
                 */

                if (pomVersion.endsWith('-SNAPSHOT')) {

                    isSnapshot = true

                    appVersion = pomVersion

                    echo "Snapshot build detected."


                }

                /*
                 * ------------------------------------------------
                 * RELEASE
                 * ------------------------------------------------
                 */

                else {

                    isSnapshot = false

                    /*
                     * Ignore POM release version.
                     *
                     * Nexus becomes the source of truth.
                     */

                    appVersion =
                        getNextReleaseVersion(
                            nexus_release_repo,
                            groupId,
                            artifactId
                        )

                    echo "Next release version: ${appVersion}"


                    /*
                     * Set Maven version.
                     */

                    sh """
                        set -e

                        export PATH="\$JAVA_HOME/bin:\$MAVEN_HOME/bin:\$PATH"

                        mvn \
                            -s settings.xml \
                            build-helper:parse-version \
                            versions:set \
                            -DnewVersion=${appVersion} \
                            versions:commit
                    """

                    /*
                     * Verify version.
                     */

                    def verifiedVersion =
                        getFromPom('version')

                    if (verifiedVersion != appVersion) {

                        error(
                            "Version mismatch! " +
                            "Expected=${appVersion}, " +
                            "Actual=${verifiedVersion}"
                        )
                    }
                }


                echo '========================================'
                echo 'VERSION INFORMATION'
                echo '========================================'
                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
                echo "Snapshot    : ${isSnapshot}"
                echo "Commit      : ${gitCommitId}"
                echo "Build       : ${BUILD_NUMBER}"
                echo '========================================'
            }


            /*
             * ====================================================
             * DUPLICATE VERSION CHECK
             * ====================================================
             */

            stage('Check Duplicate Version') {

                if (!isSnapshot) {

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'nexus-credential',
                            usernameVariable: 'NEXUS_USERNAME',
                            passwordVariable: 'NEXUS_PASSWORD'
                        )
                    ]) {

                        def groupId =
                            getFromPom('groupId')

                        def artifactId =
                            getFromPom('artifactId')

                        def groupPath =
                            groupId.replace('.', '/')

                        def versionUrl =
                            "${nexus_release_repo}" +
                            "${groupPath}/" +
                            "${artifactId}/" +
                            "${appVersion}/"


                        echo "========================================"
                        echo "DUPLICATE VERSION CHECK"
                        echo "========================================"

                        echo "GroupId    : ${groupId}"
                        echo "ArtifactId : ${artifactId}"
                        echo "Version    : ${appVersion}"

                        echo ""
                        echo "Nexus URL:"
                        echo versionUrl


                        /*
                         * Check Nexus directory/artifact.
                         *
                         * HTTP 200 = version exists
                         * HTTP 404 = version does not exist
                         */

                        def httpCode =
                            sh(
                                returnStdout: true,
                                script: """
                                    curl \
                                        -s \
                                        -o /dev/null \
                                        -w '%{http_code}' \
                                        -u "\$NEXUS_USERNAME:\$NEXUS_PASSWORD" \
                                        "${versionUrl}"
                                """
                            ).trim()


                        echo "Nexus HTTP Status: ${httpCode}"


                        if (httpCode == '200') {

                            error(
                                "DUPLICATE VERSION DETECTED: " +
                                "${groupId}:${artifactId}:${appVersion} " +
                                "sudah tersedia di Nexus!"
                            )
                        }


                        if (
                            httpCode != '404' &&
                            httpCode != '200'
                        ) {

                            error(
                                "Tidak dapat memverifikasi Nexus. " +
                                "HTTP status=${httpCode}"
                            )
                        }


                        echo ""
                        echo "Version ${appVersion} belum ada di Nexus."
                    }
                }

                else {

                    echo "Snapshot version detected."
                    echo "Duplicate release check dilewati."
                }
            }


            /*
             * ====================================================
             * BUILD
             * ====================================================
             */

            stage('Build') {

                sh '''
                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                    echo "========================================"
                    echo "MAVEN BUILD"
                    echo "========================================"

                    mvn \
                        clean package \
                        -DskipTests \
                        -s settings.xml
                '''
            }


            /*
             * ====================================================
             * UNIT TEST
             * ====================================================
             */

            stage('Unit Test') {

                sh '''
                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                    echo "========================================"
                    echo "UNIT TEST"
                    echo "========================================"

                    mvn \
                        test \
                        -s settings.xml
                '''
            }


            /*
             * ====================================================
             * INTEGRATION TEST
             * ====================================================
             */

            stage('Integration Test') {

                sh '''
                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                    echo "========================================"
                    echo "INTEGRATION TEST"
                    echo "========================================"

                    mvn \
                        failsafe:integration-test \
                        failsafe:verify \
                        -s settings.xml
                '''
            }


            /*
             * ====================================================
             * FINAL DUPLICATE CHECK
             *
             * Important:
             *
             * Check again immediately before deployment.
             *
             * This protects against:
             *
             * Build A -> version 0.0.1
             * Build B -> version 0.0.1
             *
             * ====================================================
             */

            stage('Final Duplicate Check') {

                if (!isSnapshot) {

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'nexus-credential',
                            usernameVariable: 'NEXUS_USERNAME',
                            passwordVariable: 'NEXUS_PASSWORD'
                        )
                    ]) {

                        def groupId =
                            getFromPom('groupId')

                        def artifactId =
                            getFromPom('artifactId')

                        def groupPath =
                            groupId.replace('.', '/')

                        def versionUrl =
                            "${nexus_release_repo}" +
                            "${groupPath}/" +
                            "${artifactId}/" +
                            "${appVersion}/"


                        def httpCode =
                            sh(
                                returnStdout: true,
                                script: """
                                    curl \
                                        -s \
                                        -o /dev/null \
                                        -w '%{http_code}' \
                                        -u "\$NEXUS_USERNAME:\$NEXUS_PASSWORD" \
                                        "${versionUrl}"
                                """
                            ).trim()


                        if (httpCode == '200') {

                            error(
                                "FINAL DUPLICATE CHECK FAILED: " +
                                "${appVersion} sudah tersedia di Nexus."
                            )
                        }


                        if (httpCode != '404') {

                            error(
                                "Nexus verification failed. " +
                                "HTTP=${httpCode}"
                            )
                        }


                        echo "Final duplicate check: PASS"
                    }
                }
            }


            /*
             * ====================================================
             * DEPLOY
             * ====================================================
             */

            stage('Deploy Nexus') {

                echo "========================================"
                echo "DEPLOY TO NEXUS"
                echo "========================================"

                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
                echo "Snapshot    : ${isSnapshot}"


                sh '''
                    set -e

                    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                    mvn \
                        deploy \
                        -DskipTests \
                        -s settings.xml
                '''
            }


            /*
             * ====================================================
             * VERIFY
             * ====================================================
             */

            stage('Verify') {

                sh """
                    echo "========================================"
                    echo "BUILD SUCCESS"
                    echo "========================================"

                    echo "Application : ${appName}"
                    echo "Version     : ${appVersion}"
                    echo "Snapshot    : ${isSnapshot}"
                    echo "Commit      : ${gitCommitId}"
                    echo "Build       : ${BUILD_NUMBER}"

                    echo ""
                    echo "Artifacts:"

                    find target \
                        -maxdepth 1 \
                        -type f \
                        -print
                """
            }


            /*
             * ====================================================
             * SUCCESS
             * ====================================================
             */

            echo '========================================'
            echo 'PIPELINE SUCCESS'
            echo '========================================'

            echo "Application : ${appName}"
            echo "Version     : ${appVersion}"

        }

        catch (Exception e) {

            echo '========================================'
            echo 'PIPELINE FAILED'
            echo '========================================'

            echo "Build #${BUILD_NUMBER} FAILED"

            throw e

        }

        finally {

            sh '''
                rm -f settings.xml || true
            '''

            echo "Cleanup completed."
        }
    }
}


/*
 * ============================================================
 * GET VALUE FROM POM
 * ============================================================
 */

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


/*
 * ============================================================
 * GET NEXT RELEASE VERSION FROM NEXUS
 *
 * Example:
 *
 * No release:
 *     0.0.1
 *
 * Existing:
 *     0.0.1
 *
 * Next:
 *     0.0.2
 *
 * Existing:
 *     0.0.1
 *     0.0.2
 *
 * Next:
 *     0.0.3
 *
 * ============================================================
 */

def getNextReleaseVersion(
    nexus_release_repo,
    groupId,
    artifactId
) {

    def groupPath =
        groupId.replace('.', '/')

    def metadataUrl =
        "${nexus_release_repo}" +
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

        echo "========================================"
        echo "NEXUS VERSION SEQUENCE"
        echo "========================================"

        echo "Metadata URL:"
        echo metadataUrl


        /*
         * Download metadata.
         */

        def metadata =
            sh(
                returnStdout: true,
                script: """
                    set -e

                    curl \
                        -fsS \
                        -u "\$NEXUS_USERNAME:\$NEXUS_PASSWORD" \
                        "${metadataUrl}" \
                        || true
                """
            ).trim()


        /*
         * ------------------------------------------------
         * No metadata
         * ------------------------------------------------
         */

        if (
            metadata == null ||
            metadata.trim() == '' ||
            !metadata.contains('<version>')
        ) {

            echo "No existing release found."

            echo "Next version = 0.0.1"

            return '0.0.1'
        }


        /*
         * ------------------------------------------------
         * Extract versions
         * ------------------------------------------------
         */

        def versions = []


        def matcher =
            metadata =~ /<version>([^<]+)<\/version>/


        matcher.each {

            def version =
                it[1].trim()


            /*
             * Only normal release versions:
             *
             * x.y.z
             */

            if (
                version ==~ /^\d+\.\d+\.\d+$/
            ) {

                versions << version
            }
        }


        /*
         * No valid release
         */

        if (versions.isEmpty()) {

            echo "No valid release version found."

            return '0.0.1'
        }


        /*
         * ------------------------------------------------
         * Find highest version
         * ------------------------------------------------
         */

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


                if (pa[0] != pb[0]) {

                    return pa[0] <=> pb[0]

                }


                if (pa[1] != pb[1]) {

                    return pa[1] <=> pb[1]

                }


                return pa[2] <=> pb[2]
            }


        def parts =
            maxVersion.tokenize('.').collect {
                it as Integer
            }


        def nextVersion =
            "${parts[0]}.${parts[1]}.${parts[2] + 1}"


        echo "Existing versions: ${versions.join(', ')}"
        echo "Latest version    : ${maxVersion}"
        echo "Next version      : ${nextVersion}"


        return nextVersion
    }
}


/*
 * ============================================================
 * ADD DISTRIBUTION MANAGEMENT
 * ============================================================
 */

def addDistributionToPom(
    nexus_release_repo,
    nexus_snapshot_repo
) {

    def pom = 'pom.xml'


    def distributionManagement = """
<distributionManagement>

    <repository>
        <id>nexus-releases</id>
        <name>Internal Nexus Releases</name>
        <url>${nexus_release_repo}</url>
    </repository>

    <snapshotRepository>
        <id>nexus-snapshots</id>
        <name>Internal Nexus Snapshots</name>
        <url>${nexus_snapshot_repo}</url>
    </snapshotRepository>

</distributionManagement>
"""


    def content =
        readFile(pom)


    if (
        content.contains(
            '<distributionManagement>'
        )
    ) {

        echo 'distributionManagement already exists'

    }

    else {

        def projectEnd =
            content.lastIndexOf(
                '</project>'
            )


        if (projectEnd == -1) {

            error(
                'Invalid pom.xml: </project> tidak ditemukan'
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
}


/*
 * ============================================================
 * CREATE SETTINGS.XML
 * ============================================================
 */

def prepareSettingsXml(nexus_deps_repo) {

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
            <url>${nexus_deps_repo}</url>
            <mirrorOf>*</mirrorOf>
        </mirror>

    </mirrors>

</settings>
EOF

        chmod 600 settings.xml

        echo "========================================"
        echo "settings.xml created"
        echo "========================================"

        ls -lh settings.xml
    """
}