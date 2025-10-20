
pipeline {
    agent { label 'master-node' }
    environment {
        GITLAB_CREDENTIALS_ID = 'gitlab-root-user-pass-creds-for-http'
        REGISTRY_URL = 'harbor.devops.com:8083'
        REGISTRY_CREDENTIALS = 'harbor-registry-user-pass-creds'
        IMAGE_NAME = 'testproject/test-one'
        IMAGE_TAG = 'latest'
    }

    stages {
        // Stage 1: Checkout code from GitLab
        stage('Checkout from GitLab') {
            steps {
                git branch: 'main',
                url: 'http://gitlab.devops.com:8082/root/test-one',
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
                    env.BUILD_TAG = BUILD_TAG
                }
            }
        }

        // stage 3: trivy scan
        stage('Trivy Scan') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh """
                        docker run --rm \
                            -v /var/run/docker.sock:/var/run/docker.sock \
                            -v \$HOME/.cache:/root/.cache \
                            -v \$WORKSPACE:/workspace \
                            aquasec/trivy:latest image \
                            --severity HIGH,CRITICAL --exit-code 1 \
                            --format json --output /workspace/trivy-report.json \
                            ${REGISTRY_URL}/${IMAGE_NAME}:${env.BUILD_TAG}
                    """
                }
            }
        }

        // stage 4: convert trivy json to html
        stage('Convert Trivy JSON to HTML') {
            steps {
                sh """
                docker run --rm \
                        -v \$WORKSPACE:/workspace \
                        aquasec/trivy:latest convert \
                        --format template \
                        --template "@contrib/html.tpl" \
                        --output /workspace/trivy-report.html \
                        /workspace/trivy-report.json
                """
            }
        }

        // stage 5: publish trivy report
        stage('Publish Trivy HTML Report') {
            steps {
                    // install the "html publisher" plugin.
                    publishHTML([
                        reportDir: '.',
                        reportFiles: 'trivy-report.html',
                        reportName: 'Trivy Security Report',
                        keepAll: true,
                        alwaysLinkToLastBuild: true,
                        allowMissing: true
                    ])
            }
        }
    }
}

