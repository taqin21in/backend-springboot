def git_repo = 'https://github.com/taqin21in/backend-springboot.git'
def git_branch = 'main'

def nexus_base_url = 'http://192.168.0.103:8081'
def nexus_deps_repo = "${nexus_base_url}/repository/maven-public/"
def nexus_deploy_repo = "${nexus_base_url}/repository/maven-releases/"

def appName
def appFullVersion
def gitCommitId


/*
 * ============================================================
 * RUN ON JENKINS AGENT / RUNNER
 * ============================================================
 */

node('runner') {

    stage('Environment Check') {

    echo '========================================'
    echo 'RUNNING ON JENKINS RUNNER'
    echo '========================================'

        sh '''
            set -e

            echo "===== SYSTEM ====="
            hostname
            whoami
            pwd

            echo ""
            echo "===== JAVA ====="
            echo "JAVA_HOME=$JAVA_HOME"
            which java
            readlink -f $(which java)
            java -version

            echo ""
            echo "===== MAVEN ====="
            echo "MAVEN_HOME=$MAVEN_HOME"
            echo "M2_HOME=$M2_HOME"
            echo "MAVEN_OPTS=$MAVEN_OPTS"
            echo "PATH=$PATH"

            which mvn
            type -a mvn
            readlink -f $(which mvn)
            mvn -version

            echo ""
            echo "===== MAVEN ASM ====="
            ls -lah /opt/maven/lib/asm-9.8.jar
            jar tf /opt/maven/lib/asm-9.8.jar | \
                grep 'org/objectweb/asm/ClassVisitor.class'

            echo ""
            echo "===== GIT ====="
            git --version

            echo ""
            echo "===== DOCKER ====="
            docker version
        '''
    }


    /*
     * ========================================================
     * CHECKOUT
     * ========================================================
     */

    stage('Checkout') {

        echo '========================================'
        echo 'CHECKOUT SOURCE CODE'
        echo '========================================'

        deleteDir()

        git(
            url: git_repo,
            branch: git_branch,
            credentialsId: 'github-credential'
        )

        sh '''
            echo ""
            echo "Git commit:"
            git rev-parse HEAD

            echo ""
            echo "Git branch:"
            git branch --show-current

            echo ""
            echo "Project files:"
            ls -la
        '''
    }


    /*
     * ========================================================
     * CHECK POM
     * ========================================================
     */

    stage('Check Maven Project') {

        sh '''
            set -e

            echo "========================================"
            echo "CHECKING MAVEN PROJECT"
            echo "========================================"

            if [ ! -f pom.xml ]; then
                echo "ERROR: pom.xml tidak ditemukan!"
                exit 1
            fi

            echo ""
            echo "pom.xml ditemukan."

            echo ""
            echo "===== GROUP ID ====="
            mvn help:evaluate \
                -Dexpression=project.groupId \
                -q \
                -DforceStdout

            echo ""
            echo "===== ARTIFACT ID ====="
            mvn help:evaluate \
                -Dexpression=project.artifactId \
                -q \
                -DforceStdout

            echo ""
            echo "===== VERSION ====="
            mvn help:evaluate \
                -Dexpression=project.version \
                -q \
                -DforceStdout
        '''
    }


    /*
     * ========================================================
     * PREPARE
     * ========================================================
     */

    stage('Prepare') {

        echo '========================================'
        echo 'PREPARE MAVEN + NEXUS'
        echo '========================================'

        withCredentials([
            usernamePassword(
                credentialsId: 'nexus-credential',
                usernameVariable: 'NEXUS_USERNAME',
                passwordVariable: 'NEXUS_PASSWORD'
            )
        ]) {

            prepareSettingsXml(
                nexus_deps_repo,
                NEXUS_USERNAME,
                NEXUS_PASSWORD
            )

            addDistributionToPom(
                nexus_deploy_repo
            )
        }


        appName = getFromPom('name')

        if (appName == null || appName.trim() == '') {
            appName = getFromPom('artifactId')
        }


        /*
         * Change version:
         *
         * 0.0.1-SNAPSHOT
         *
         * menjadi:
         *
         * 0.0.BUILD_NUMBER
         */

        sh '''
            set -e

            mvn \
                -s settings.xml \
                build-helper:parse-version \
                versions:set \
                "-DnewVersion=\\${parsedVersion.majorVersion}.\\${parsedVersion.minorVersion}.${BUILD_NUMBER}" \
                versions:commit
        '''


        appFullVersion = getFromPom('version')

        gitCommitId = sh(
            returnStdout: true,
            script: 'git rev-parse HEAD'
        ).trim()


        echo '========================================'
        echo "Application : ${appName}"
        echo "Version     : ${appFullVersion}"
        echo "Git Commit  : ${gitCommitId}"
        echo "Build       : ${BUILD_NUMBER}"
        echo '========================================'
    }


    /*
     * ========================================================
     * BUILD
     * ========================================================
     */

    stage('Build') {

        echo '========================================'
        echo 'MAVEN BUILD'
        echo '========================================'

        sh '''
            set -e

            mvn \
                clean package \
                -DskipTests \
                -s settings.xml
        '''
    }


    /*
     * ========================================================
     * UNIT TEST
     * ========================================================
     */

    stage('Test') {

        echo '========================================'
        echo 'UNIT TEST'
        echo '========================================'

        sh '''
            set -e

            mvn \
                test \
                -s settings.xml
        '''
    }


    /*
     * ========================================================
     * INTEGRATION TEST
     * ========================================================
     */

    stage('Integration Tests') {

        echo '========================================'
        echo 'INTEGRATION TEST'
        echo '========================================'

        sh '''
            set -e

            mvn \
                failsafe:integration-test \
                -s settings.xml
        '''
    }


    /*
     * ========================================================
     * ARCHIVE TO NEXUS
     * ========================================================
     */

    stage('Archive') {

        echo '========================================'
        echo 'DEPLOY ARTIFACT TO NEXUS'
        echo '========================================'

        sh '''
            set -e

            mvn \
                deploy \
                -DskipTests \
                -s settings.xml
        '''
    }


    /*
     * ========================================================
     * VERIFY
     * ========================================================
     */

    stage('Verify Nexus') {

        echo '========================================'
        echo 'BUILD RESULT'
        echo '========================================'

        sh """
            echo "Application : ${appName}"
            echo "Version     : ${appFullVersion}"
            echo "Git Commit  : ${gitCommitId}"

            echo ""
            echo "Generated artifacts:"

            find target \
                -maxdepth 1 \
                -type f \
                -print
        """
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
 * ADD DISTRIBUTION MANAGEMENT
 * ============================================================
 */

def addDistributionToPom(nexus_deploy_repo) {

    def pom = 'pom.xml'

    def distributionManagement = """
<distributionManagement>

    <repository>
        <id>internal.repo</id>
        <name>Internal Nexus Releases</name>
        <url>${nexus_deploy_repo}</url>
    </repository>

    <snapshotRepository>
        <id>internal.repo</id>
        <name>Internal Nexus Snapshots</name>
        <url>${nexus_deploy_repo}</url>
    </snapshotRepository>

</distributionManagement>
"""


    def content = readFile(pom)


    if (content.contains('<distributionManagement>')) {

        echo 'distributionManagement already exists'

    } else {

        def projectEnd = content.lastIndexOf('</project>')

        if (projectEnd == -1) {
            error 'Invalid pom.xml: </project> tidak ditemukan'
        }


        def newContent =
            content.substring(0, projectEnd) +
            distributionManagement +
            content.substring(projectEnd)


        writeFile(
            file: pom,
            text: newContent
        )


        echo 'distributionManagement added to pom.xml'
    }
}


/*
 * ============================================================
 * CREATE SETTINGS.XML
 * ============================================================
 */

def prepareSettingsXml(
    nexus_deps_repo,
    nexus_username,
    nexus_password
) {

def settingsXML = """<?xml version="1.0" encoding="UTF-8"?>

<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0
          https://maven.apache.org/xsd/settings-1.2.0.xsd">

    <servers>
        <server>
            <id>nexus-releases</id>
            <username>${env.NEXUS_USERNAME}</username>
            <password>${env.NEXUS_PASSWORD}</password>
        </server>

        <server>
            <id>nexus-snapshots</id>
            <username>${env.NEXUS_USERNAME}</username>
            <password>${env.NEXUS_PASSWORD}</password>
        </server>

        <server>
            <id>nexus-public</id>
            <username>${env.NEXUS_USERNAME}</username>
            <password>${env.NEXUS_PASSWORD}</password>
        </server>
    </servers>

    <mirrors>
        <mirror>
            <id>nexus-public</id>
            <name>Nexus Public Repository</name>
            <url>http://192.168.0.103:8081/repository/maven-public/</url>
            <mirrorOf>*</mirrorOf>
        </mirror>
    </mirrors>

</settings>
"""


    writeFile(
        file: 'settings.xml',
        text: settingsXML
    )


    sh '''
        echo "========================================"
        echo "Maven settings.xml created"
        echo "========================================"

        ls -lh settings.xml
    '''
}