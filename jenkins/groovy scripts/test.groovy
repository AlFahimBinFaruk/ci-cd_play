// pipeline {
//     agent { label 'master-node'} // tag your agent
//     environment {
//         //any variables
//         MY_KEY = "myvalue"
//     }
//     stages {
//         // stage1 - basics
//         stage('Basics - Jenkins server commands') {
//             steps {
//                 sh 'whoami'
//                 sh 'pwd'
//                 sh 'ls -larth'
//                 sh 'docker ps'
//             }
//         }
//         // stage2 - docker build & run locally via jenkins pipeline:
//         stage('Docker Build & Run Locally') {
//             steps {
//                 sh '''
//                 cd /home/bs00927/Fahim/DevOps/lndlab-1/demo_apps/flaskapp-develop
//                 pwd
//                 ls -la
//                 docker build -t hello-docker .
//                 docker run -d -p 5001:5000 hello-docker || true
//                 docker ps | grep -i "hello-docker" || true
//                 '''
//             }
//         }
//     } //end of all stages
// } //end of full pipeline

// Make sure to create an testproject in harbor registry.
// Extensions : gitlab , docker pipeline.
pipeline {
    agent { label 'master-node' }
    environment {
        REGISTRY_URL = "harbor.devops.com:8083"
        REGISTRY_CREDENTIALS = "harbor-registry-user-pass-creds"
        IMAGE_NAME= "testproject/hello-docker"
        IMAGE_TAG= "latest"
        GITLAB_CREDENTIALS_ID = 'gitlab-root-user-pass-creds-for-http'
    }
    // Stage 1: Git Checkout:
    stages {
        // Stage 1: Checkout code from GitLab
        stage('Checkout from GitLab') {
            steps {
                git branch: 'main',
                url: 'http://gitlab.devops.com:8082/root/test-one.git',
                credentialsId: "${GITLAB_CREDENTIALS_ID}"
            }
        }
        // Stage 2: Docker Build & Push to registry:
        stage('Docker Build & Push') {
            steps {
                script {
                    def BUILD_TAG = "build-${env.BUILD_NUMBER}"
                    docker.withRegistry("https://${REGISTRY_URL}", "${REGISTRY_CREDENTIALS}") {
                        // Build Docker image from Git checkout folder
                        def app = docker.build("${REGISTRY_URL}/${IMAGE_NAME}:${BUILD_TAG}", "${env.WORKSPACE}")
                        // Push tags
                        app.push()
                        // push build-number tag
                        app.push("${IMAGE_TAG}")
                        // push 'latest' tag
                    }
                }
            }
        }
        // Stage 3: Docker Compose Deployment:
        stage('Docker Compose Deploy') {
            steps {
            script {
                def BUILD_TAG = "build-${env.BUILD_NUMBER}"
                    withCredentials([usernamePassword(credentialsId: "${REGISTRY_CREDENTIALS}",
                    usernameVariable: 'HARBOR_USER',
                    passwordVariable: 'HARBOR_PASS')]) {
                        withEnv([
                        "APP_TAG=${BUILD_TAG}",
                        "REGISTRY_URL=${REGISTRY_URL}",
                        "IMAGE_NAME=${IMAGE_NAME}"
                    ]) {
                        sh """
                        echo $HARBOR_PASS | docker login ${REGISTRY_URL} -u $HARBOR_USER --password-stdin
                        docker compose pull
                        docker compose down
                        docker compose up -d
                        docker logout ${REGISTRY_URL}
                        """
                        }
                    }
                }
            }
        }
    }
    post {
        success {
            echo "Deployment successful! You can rollback using any previous build tag."
        }
        failure {
            echo "Build or deploy failed."
        }
    }
}
