def git_repo = 'https://github.com/taqin21in/backend-springboot.git'
def git_branch = 'main'

def nexus_base_url = 'http://192.168.0.103:8081'
def nexus_deps_repo = "$nexus_base_url/repository/maven-public/"
def nexus_deploy_repo = "$nexus_base_url/repository/maven-releases/"

def appName
def appFullVersion
def gitCommitId


label('runner') {

    stage('Checkout') {

        echo '=== CHECKOUT ==='

        git(
            url: "${git_repo}",
            branch: "${git_branch}",
            credentialsId: 'github-credential'
        )
    }


    stage('Prepare') {

        echo '=== PREPARE ==='

        withCredentials([
            usernamePassword(
                credentialsId: 'nexus-credential',
                usernameVariable: 'nexus_username',
                passwordVariable: 'nexus_password'
            )
        ]) {

            prepareSettingsXml(
                nexus_deps_repo,
                nexus_username,
                nexus_password
            )

            addDistributionToPom(
                nexus_deploy_repo
            )
        }

        appName = getFromPom('name')

        if (appName == null || appName.trim() == '') {
            appName = getFromPom('artifactId')
        }

        echo "Application Name: ${appName}"


        /*
         * Version:
         *
         * 1.0.0
         *
         * menjadi:
         *
         * 1.0.BUILD_NUMBER
         */

        sh '''
            mvn \
              -s settings.xml \
              build-helper:parse-version \
              versions:set \
              -DnewVersion=\\${parsedVersion.majorVersion}.\\${parsedVersion.minorVersion}.${BUILD_NUMBER} \
              versions:commit
        '''

        appFullVersion = getFromPom('version')

        gitCommitId = sh(
            returnStdout: true,
            script: 'git rev-parse HEAD'
        ).trim()

        echo "================================"
        echo "Application : ${appName}"
        echo "Version     : ${appFullVersion}"
        echo "Git Commit  : ${gitCommitId}"
        echo "Build       : ${BUILD_NUMBER}"
        echo "================================"
    }


    stage('Environment Check') {

        echo '=== RUNNING ON RUNNER ==='

        sh '''
            echo "Hostname:"
            hostname

            echo ""
            echo "User:"
            whoami

            echo ""
            echo "Working Directory:"
            pwd

            echo ""
            echo "Java:"
            java -version

            echo ""
            echo "Maven:"
            mvn -version

            echo ""
            echo "Git:"
            git --version

            echo ""
            echo "Docker:"
            docker version
        '''
    }


    stage('Build') {

        echo '=== MAVEN BUILD ==='

        sh '''
            mvn \
              clean package \
              -DskipTests \
              -s settings.xml
        '''
    }


    stage('Test') {

        echo '=== UNIT TEST ==='

        sh '''
            mvn \
              test \
              -s settings.xml
        '''
    }


    stage('Integration Tests') {

        echo '=== INTEGRATION TEST ==='

        sh '''
            mvn \
              failsafe:integration-test \
              -s settings.xml
        '''
    }


    stage('Archive') {

        echo '=== DEPLOY ARTIFACT TO NEXUS ==='

        sh '''
            mvn \
              deploy \
              -DskipTests \
              -s settings.xml
        '''
    }


    stage('Verify Nexus') {

        echo '=== BUILD RESULT ==='

        sh '''
            echo "Application : ${appName}"
            echo "Version     : ${appFullVersion}"

            echo ""
            echo "Generated artifacts:"
            find target -maxdepth 1 -type f -print
        '''
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
            -Dexec.executable=echo \
            -Dexec.args='\\\${project.${key}}' \
            --non-recursive \
            exec:exec
        """
    ).trim()
}


/*
 * ============================================================
 * ADD DISTRIBUTION MANAGEMENT
 * ============================================================
 */

def addDistributionToPom(nexus_deploy_repo) {

    pom = 'pom.xml'

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

    content = readFile(pom)

    /*
     * Jangan duplicate distributionManagement
     */

    if (content.contains('<distributionManagement>')) {

        echo 'distributionManagement already exists'

    } else {

        newContent =
            content.substring(
                0,
                content.lastIndexOf('</project>')
            ) +
            distributionManagement +
            '</project>'

        writeFile(
            file: pom,
            text: newContent
        )

        echo 'distributionManagement added'
    }
}


/*
 * ============================================================
 * CREATE MAVEN SETTINGS.XML
 * ============================================================
 */

def prepareSettingsXml(
    nexus_deps_repo,
    nexus_username,
    nexus_password
) {

    def settingsXML = """<?xml version="1.0" encoding="UTF-8"?>

<settings
    xmlns="http://maven.apache.org/SETTINGS/1.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="
        http://maven.apache.org/SETTINGS/1.0.0
        https://maven.apache.org/xsd/settings-1.0.0.xsd">

    <mirrors>

        <mirror>

            <id>nexus</id>

            <mirrorOf>*</mirrorOf>

            <url>${nexus_deps_repo}</url>

        </mirror>

    </mirrors>


    <profiles>

        <profile>

            <id>nexus</id>

            <activation>

                <activeByDefault>true</activeByDefault>

            </activation>


            <repositories>

                <repository>

                    <id>central</id>

                    <url>https://repo1.maven.org/maven2/</url>

                    <releases>
                        <enabled>true</enabled>
                    </releases>

                    <snapshots>
                        <enabled>true</enabled>
                    </snapshots>

                </repository>

            </repositories>


            <pluginRepositories>

                <pluginRepository>

                    <id>central</id>

                    <url>https://repo1.maven.org/maven2/</url>

                    <releases>
                        <enabled>true</enabled>
                    </releases>

                    <snapshots>
                        <enabled>true</enabled>
                    </snapshots>

                </pluginRepository>

            </pluginRepositories>

        </profile>

    </profiles>


    <activeProfiles>

        <activeProfile>nexus</activeProfile>

    </activeProfiles>


    <servers>

        <server>

            <id>internal.repo</id>

            <username>${nexus_username}</username>

            <password>${nexus_password}</password>

        </server>

    </servers>

</settings>
"""

    writeFile(
        file: 'settings.xml',
        text: settingsXML
    )

    sh '''
        echo "Maven settings.xml:"
        ls -lh settings.xml
    '''
}