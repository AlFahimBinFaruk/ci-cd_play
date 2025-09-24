pipeline {
    agent { label 'master-node' }

    environment {
        REGISTRY_URL          = "harbor.devops.com:8083"
        REGISTRY_CREDENTIALS  = "harbor-registry-user-pass-creds"
        IMAGE_NAME            = "testproject/hello-docker"
        IMAGE_TAG             = "latest"
        GITLAB_CREDENTIALS_ID = "gitlab-root-user-pass-creds-for-http"
    }

    stages {
        stage('Checkout from GitLab') {
            steps {
                git branch: 'develop',
                    url: 'http://172.16.229.70:8082/devops/flaskapp.git',
                    credentialsId: "${GITLAB_CREDENTIALS_ID}"
            }
        }

        stage('Docker Build & Push') {
            steps {
                script {
                    def BUILD_TAG = "build-${env.BUILD_NUMBER}"
                    docker.withRegistry("https://${REGISTRY_URL}", "${REGISTRY_CREDENTIALS}") {
                        def app = docker.build("${REGISTRY_URL}/${IMAGE_NAME}:${BUILD_TAG}", "${env.WORKSPACE}")
                        app.push()
                        app.push("${IMAGE_TAG}")
                    }
                    env.BUILD_TAG = BUILD_TAG
                }
            }
        }

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
                            ${REGISTRY_URL}/${IMAGE_NAME}:\${BUILD_TAG}
                    """
                }
            }
        }

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

stage('Publish Trivy HTML Report') {
    steps {
        
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

