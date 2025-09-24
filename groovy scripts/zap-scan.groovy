pipeline {
    agent { label 'master-node' }
    environment {
        ZAP_DOCKER_IMAGE = 'ghcr.io/zaproxy/zaproxy:stable'
        // local laptop ip as zap is running in docker
        APP_URL = 'http://172.16.218.42:8000/'
        REPORT_HTML = 'zap-report.html'
        REPORT_JSON = 'zap-report.json'
    }
    //started zap stages
    stages {
        stage('Prepare Workspace') {
            steps {
                script {
                    sh '''
                        # Create a clean directory for this build
                        mkdir -p zap-output
                        chmod -R 777 zap-output
                        cd zap-output
                        # Pre-create files to ensure correct ownership
                        touch ${REPORT_HTML} ${REPORT_JSON} gen.conf zap.yaml
                        chmod 666 ${REPORT_HTML} ${REPORT_JSON} gen.conf zap.yaml
                    '''
                }
            }
        }
        stage('Run ZAP Scan') {
            steps {
                script {
                    sh '''
                        # Run ZAP baseline scan using the dedicated directory
                        docker run --rm \
                        -v $(pwd)/zap-output:/zap/wrk/:rw \
                        -u 0:0 \
                        -t ${ZAP_DOCKER_IMAGE} \
                        zap-baseline.py \
                        -t ${APP_URL} \
                        -r ${REPORT_HTML} \
                        -J ${REPORT_JSON} || true
                        # Use another container to fix permissions issues
                        docker run --rm \
                        -v $(pwd)/zap-output:/data \
                        -u 0:0 \
                        alpine:latest \
                        sh -c "chown -R $(id -u):$(id -g) /data/* || true"
                    '''
                }
            }
        }
        stage('Publish ZAP Report') {
            steps {
                script {
                    // Use Groovy string interpolation for the report name
                    publishHTML([
                        reportDir: 'zap-output',
                        reportFiles: "${REPORT_HTML}",
                        reportName: "ZAP_Scan_Report_${APP_URL}",
                        keepAll: true,
                        alwaysLinkToLastBuild: true,
                        allowMissing: true
                    ])
                }
            }
        }
}//end of zap stages
    //starting post cleanup
    post {
        always {
            script {
                // Safely handle old output directory cleanup and renaming
                sh '''
                    # Use Docker to handle permission-sensitive operations
                    docker run --rm \
                    -v $(pwd):/workspace \
                    -u 0:0 \
                    alpine:latest \
                    sh -c "
                    cd /workspace && \
                    if [ -d 'zap-output' ]; then \
                    mv zap-output zap-output-old || true; \
                    fi
                    "
                '''
                // Clean workspace except for essential files
                cleanWs(
                    deleteDirs: true,
                    patterns: [
                    [pattern: 'zap-*', type: 'EXCLUDE'],
                    // [pattern: '*.groovy', type: 'EXCLUDE'],
                    // [pattern: '*.properties', type: 'EXCLUDE']
                    ]
                )
            }
        }
    }//end of post activity
} //end of pipeline
