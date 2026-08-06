pipeline {
    agent any

    environment {
        IMAGE_NAME = "host.docker.internal:8082/docker-hosted/backend-springboot"
        IMAGE_TAG  = "1.0"
    }

    stages {
        
        stage('Check POM') {
        steps {
            bat 'dir'
            bat 'type pom.xml'
        }
        }
        stage('Check Docker') {
            steps {
                bat 'docker version'
                bat 'docker info'
            }
        }
        stage('Compile') {
        steps {
            bat 'mvn clean compile'
            }
        }
        stage('Unit Test') {
        steps {
            bat 'mvn test'
        }
        }
        stage('SonarQube Analysis') {
        steps {
            withSonarQubeEnv('SonarQube') {
                bat 'mvn sonar:sonar'
            }
        }
        }
        stage('Package') {
        steps {
            bat 'mvn clean package -DskipTests'
        }
        }
        stage('Docker Login') {
        steps {
            withCredentials([usernamePassword(
                credentialsId: 'nexus-credential',
                usernameVariable: 'USERNAME',
                passwordVariable: 'PASSWORD'
            )]) {

                bat '''
                docker login host.docker.internal:8082 ^
                -u %USERNAME% ^
                -p %PASSWORD%
                '''
            }
        }
        }
       stage('Build Docker Image') {
       steps {
            bat '''
            docker build -t %IMAGE_NAME%:%IMAGE_TAG% .
            '''
        }
        }
        stage('Push Image') {
        steps {
            bat '''
            docker push %IMAGE_NAME%:%IMAGE_TAG%
            '''
        }
        }
        stage('Deploy') {
        steps {
            bat '''
            docker rm -f backend-springboot || exit 0

            docker run -d ^
                --name backend-springboot ^
                -p 8080:8080 ^
                %IMAGE_NAME%:%IMAGE_TAG%
            '''
        }
        }

        stage('Verify Deployment') {
        steps {
            bat '''
            docker ps
            docker images
            '''
        }
        }
    }
    post {
        success {
            echo 'Pipeline SUCCESS'
        }

        failure {
            echo 'Pipeline FAILED'
        }

        always {
            cleanWs()
        }
        }
}

