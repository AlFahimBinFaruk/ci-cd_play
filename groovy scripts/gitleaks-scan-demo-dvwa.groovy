pipeline {
    agent { label 'master-node' }
    environment {
        GITLAB_CREDENTIALS_ID = 'gitlab-root-user-pass-creds-for-http'
    }

    stages {
        stage('Checkout from GitLab') {
            steps {
                git branch: 'main',
                    url: 'http://gitlab.devops.com:8082/root/test-one.git',
                    credentialsId: "${GITLAB_CREDENTIALS_ID}"
            }
        }
        
        stage('Gitleaks Scan') {
            steps {
                script {
                        sh """
                            mkdir -p gitleaks-output
        
                            docker run --rm \\
                                -v \$(pwd):/path \\
                                ghcr.io/gitleaks/gitleaks:latest detect \\
                                --source="/path" \\
                                --no-git \\
                                --verbose \\
                                --report-format json \\
                                --report-path=/path/gitleaks-output/gitleaks-report.json \\
                                --redact || exit 0
                        """
                }
            }
        }
    }
}

// to see result of scan: click on build no -> workspaces