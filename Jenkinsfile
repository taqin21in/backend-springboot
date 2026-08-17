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
    
    withEnv([
        'JAVA_HOME=/usr/lib/jvm/java-21-openjdk-21.0.12.0.8-1.2.el9_8.x86_64',
        'MAVEN_HOME=/opt/maven'
    ]) {
        stages {


            /*
            * ====================================================
            * ENVIRONMENT
            * ====================================================
            */

            stage('Environment Check') {

                steps {

                    sh '''
                        set -e

                        export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                        echo "========================================"
                        echo "ENVIRONMENT"
                        echo "========================================"

                        hostname
                        whoami
                        pwd

                        echo ""
                        java -version

                        echo ""
                        mvn -version

                        echo ""
                        git --version
                    '''
                }
            }


            /*
            * ====================================================
            * CHECKOUT
            * ====================================================
            */

            stage('Checkout') {

                steps {

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
            }


            /*
            * ====================================================
            * MAVEN PROJECT
            * ====================================================
            */

            stage('Check Maven Project') {

                steps {

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
            }


            /*
            * ====================================================
            * PREPARE NEXUS
            * ====================================================
            */

            stage('Prepare Nexus') {

                steps {

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'nexus-credential',
                            usernameVariable: 'NEXUS_USERNAME',
                            passwordVariable: 'NEXUS_PASSWORD'
                        )
                    ]) {

                        script {

                            prepareSettingsXml(
                                nexus_deps_repo
                            )

                            addDistributionToPom(
                                nexus_release_repo,
                                nexus_snapshot_repo
                            )
                        }
                    }
                }
            }


            /*
            * ====================================================
            * DETERMINE VERSION
            * ====================================================
            */

            stage('Determine Version') {

                steps {

                    script {

                        def pomVersion =
                            getFromPom('version')

                        echo "POM Version: ${pomVersion}"


                        /*
                        * ------------------------------------------------
                        * SNAPSHOT
                        * ------------------------------------------------
                        */

                        if (pomVersion.endsWith('-SNAPSHOT')) {

                            isSnapshot = true

                            appVersion = pomVersion

                        }


                        /*
                        * ------------------------------------------------
                        * RELEASE
                        * ------------------------------------------------
                        */

                        else {

                            isSnapshot = false

                            appVersion = pomVersion

                        }


                        appName =
                            getFromPom('artifactId')


                        gitCommitId =
                            sh(
                                returnStdout: true,
                                script: 'git rev-parse HEAD'
                            ).trim()


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
                }
            }


            /*
            * ====================================================
            * DUPLICATE VERSION CHECK
            * ====================================================
            */

            stage('Check Duplicate Version') {

                steps {

                    script {

                        if (!isSnapshot) {

                            echo '========================================'
                            echo 'CHECK NEXUS DUPLICATE VERSION'
                            echo '========================================'


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


                                /*
                                * Convert:
                                *
                                * com.example
                                *
                                * menjadi:
                                *
                                * com/example
                                */

                                def groupPath =
                                    groupId.replace('.', '/')


                                def metadataUrl =
                                    "${nexus_release_repo}" +
                                    "${groupPath}/" +
                                    "${artifactId}/" +
                                    "${appVersion}/"


                                echo "Nexus path:"
                                echo metadataUrl


                                def status =
                                    sh(
                                        returnStatus: true,
                                        script: """
                                            curl \
                                                -s \
                                                -o /dev/null \
                                                -w '%{http_code}' \
                                                -u "\$NEXUS_USERNAME:\$NEXUS_PASSWORD" \
                                                "${metadataUrl}" \
                                                | grep -E '^200\$'
                                        """
                                    )


                                if (status == 0) {

                                    error(
                                        "DUPLICATE VERSION DETECTED: " +
                                        "${groupId}:${artifactId}:${appVersion} " +
                                        "sudah ada di Nexus!"
                                    )
                                }


                                echo "Version ${appVersion} belum ada di Nexus."
                            }
                        }


                        else {

                            echo "Snapshot version detected."
                            echo "Duplicate check dilewati."
                        }
                    }
                }
            }


            /*
            * ====================================================
            * BUILD
            * ====================================================
            */

            stage('Build') {

                steps {

                    sh '''
                        set -e

                        export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                        mvn \
                            clean package \
                            -DskipTests \
                            -s settings.xml
                    '''
                }
            }


            /*
            * ====================================================
            * UNIT TEST
            * ====================================================
            */

            stage('Unit Test') {

                steps {

                    sh '''
                        set -e

                        export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                        mvn \
                            test \
                            -s settings.xml
                    '''
                }
            }


            /*
            * ====================================================
            * INTEGRATION TEST
            * ====================================================
            */

            stage('Integration Test') {

                steps {

                    sh '''
                        set -e

                        export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                        mvn \
                            failsafe:integration-test \
                            failsafe:verify \
                            -s settings.xml
                    '''
                }
            }


            /*
            * ====================================================
            * DEPLOY
            * ====================================================
            */

            stage('Deploy Nexus') {

                steps {

                    echo "Deploying version ${appVersion}"

                    sh '''
                        set -e

                        export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

                        mvn \
                            deploy \
                            -DskipTests \
                            -s settings.xml
                    '''
                }
            }


            /*
            * ====================================================
            * VERIFY
            * ====================================================
            */

            stage('Verify') {

                steps {

                    sh """
                        echo "========================================"
                        echo "BUILD SUCCESS"
                        echo "========================================"

                        echo "Application : ${appName}"
                        echo "Version     : ${appVersion}"
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
            }
        }


        /*
        * ========================================================
        * POST
        * ========================================================
        */

        post {

            success {

                echo '========================================'
                echo 'PIPELINE SUCCESS'
                echo '========================================'

                echo "Application : ${appName}"
                echo "Version     : ${appVersion}"
            }


            failure {

                echo '========================================'
                echo 'PIPELINE FAILED'
                echo '========================================'

                echo "Build #${BUILD_NUMBER} FAILED"
            }


            always {

                sh '''
                    rm -f settings.xml || true
                '''
            }
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


    def content = readFile(pom)


    if (content.contains('<distributionManagement>')) {

        echo 'distributionManagement already exists'

    }

    else {

        def projectEnd =
            content.lastIndexOf('</project>')


        if (projectEnd == -1) {

            error(
                'Invalid pom.xml: </project> tidak ditemukan'
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


        echo 'distributionManagement added.'
    }
}


/*
 * ============================================================
 * CREATE SETTINGS.XML
 * ============================================================
 */

def prepareSettingsXml(nexus_deps_repo) {

    sh '''
        set -eu

        cat > settings.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>

<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0
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
            <url>${nexus_deps_repo}</url>
            <mirrorOf>*</mirrorOf>
        </mirror>

    </mirrors>

</settings>
EOF

        chmod 600 settings.xml

        echo "settings.xml created."
    '''
}