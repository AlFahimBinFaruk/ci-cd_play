pipeline {
    agent { label 'master-node' }

    environment {
        ZAP_DOCKER_IMAGE = 'ghcr.io/zaproxy/zaproxy:stable'
        APP_URL = 'http://172.16.229.70:3000'
        REPORT_HTML = 'zap-report.html'
        REPORT_JSON = 'zap-report.json'
    }

    stages {
        stage('Run ZAP Scan') {
            steps {
                script {
                    sh '''
                        # Ensure workspace permissions
                        chmod -R 777 $(pwd)
                        chown jenkins:jenkins $(pwd) -R

                        # Run ZAP baseline scan as root to avoid permission issues
                        docker run --rm \
                            -v $(pwd):/zap/wrk/:rw \
                            -u 0:0 \
                            -t ${ZAP_DOCKER_IMAGE} \
                            zap-baseline.py \
                            -t ${APP_URL} \
                            -r ${REPORT_HTML} \
                            -J ${REPORT_JSON} || true
                    '''
                }
            }
        }

        stage('Publish ZAP Report') {
            steps {
                publishHTML([
                    reportDir: '.', 
                    reportFiles: "${REPORT_HTML}", 
                    reportName: 'ZAP Scan Report', 
                    keepAll: true, 
                    alwaysLinkToLastBuild: true, 
                    allowMissing: true
                ])
            }
        }
    }
}

