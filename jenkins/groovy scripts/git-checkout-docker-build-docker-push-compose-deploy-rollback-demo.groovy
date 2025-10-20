pipeline {
    agent { label 'master-node' }

    parameters {
        string(name: 'ROLLBACK_TAG', defaultValue: '', description: 'Set previous build tag to rollback (e.g., build-30)')
    }

    environment {
        REGISTRY_URL         = "harbor.devops.com:8083"
        REGISTRY_CREDENTIALS = "harbor-registry-user-pass-creds"
        IMAGE_NAME           = "testproject/hello-docker"
        IMAGE_TAG            = "latest"
        GITLAB_CREDENTIALS_ID = 'gitlab-root-user-pass-creds-for-http'
    }

//1.git repo checkout:
    stages {
        stage('Checkout from GitLab') {
            steps {
                git branch: 'develop',
                    url: 'http://172.16.229.70:8082/devops/flaskapp.git',
                    credentialsId: "${GITLAB_CREDENTIALS_ID}"
            }
        }

//2.docker build and image push to registry
        stage('Docker Build & Push') {
            when { expression { params.ROLLBACK_TAG == '' } } // Skip build if rolling back
            steps {
                script {
                    def BUILD_TAG = "build-${env.BUILD_NUMBER}"
                    docker.withRegistry("https://${REGISTRY_URL}", "${REGISTRY_CREDENTIALS}") {
                        def app = docker.build("${REGISTRY_URL}/${IMAGE_NAME}:${BUILD_TAG}", "${env.WORKSPACE}")
                        app.push()
                        app.push("${IMAGE_TAG}")
                    }
                }
            }
        }
        
        // 2.5 Manual approval stage
        stage('Approval Before Deploy') {
            steps {
                script {
                    timeout(time: 10, unit: 'MINUTES') {   // auto-fails after 10 min if no approval
                        input message: "Approve deployment to Docker Compose?",
                              ok: "Deploy Now"
                    }
                }
            }
        }        

//3.docker compose up & deploy stage
        stage('Docker Compose Deploy') {
            steps {
                script {
                    def BUILD_TAG = params.ROLLBACK_TAG ?: "build-${env.BUILD_NUMBER}" // Use rollback tag if provided
                    withCredentials([usernamePassword(credentialsId: "${REGISTRY_CREDENTIALS}",
                                          usernameVariable: 'HARBOR_USER',
                                          passwordVariable: 'HARBOR_PASS')]) {
                        withEnv([
                            "APP_TAG=${BUILD_TAG}",
                            "REGISTRY_URL=${REGISTRY_URL}",
                            "IMAGE_NAME=${IMAGE_NAME}"
                        ]) {
                            sh """
                                echo \$HARBOR_PASS | docker login ${REGISTRY_URL} -u \$HARBOR_USER --password-stdin
                                cd ${env.WORKSPACE}
                                
                                # bring down any existing stack first
                                docker compose down --remove-orphans || true
                                
                                docker compose pull
                                
                                docker compose up -d
                                docker ps | grep -iE "app|nginx" || true
                                docker logout ${REGISTRY_URL}
                            """
                        }
                    }
                }
            }
        }
    }

//4.post deployment clean up or workspace clean up ...
//     post {
//     success {
//         echo "Deployment successful! You can rollback using any previous build tag."
//         cleanWs()   // clean workspace after successful build
//     }
//     failure {
//         echo "Build or deploy failed."
//         cleanWs()   // also clean workspace if build fails
//     }
//     always {
//         cleanWs()   // ensures cleanup happens no matter what
//     }
// }

    
} //end of all stage - thank you!

