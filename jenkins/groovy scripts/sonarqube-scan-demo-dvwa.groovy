//1. sonarqube:
pipeline {
    agent { label 'master-node' }
    environment {
        GITLAB_CREDENTIALS_ID = 'gitlab-root-user-pass-creds-for-http'
        SONAR_TOKEN = credentials('sonarqube-token-jenkins-global')
    }

    stages {
        stage('SonarQube Scan Stage') {
            steps {
                script {
                    def repos = [
                    [name: 'devops/dvwa', projectKey: 'sonar-dvwa', branch: 'master']
                    ]

                    def scannerHome = tool 'sonar-scanner1'

                    repos.each { repo ->
                        stage("SonarQube Scan - ${repo.name}") {
                            dir("${repo.name}") {
                                git branch: repo.branch,
                                    credentialsId: "${GITLAB_CREDENTIALS_ID}",
                                    url: "http://172.16.229.70:8082/${repo.name}.git"

                                withSonarQubeEnv('my-sonarqube-server-url') {
                                    sh """
                                        ${scannerHome}/bin/sonar-scanner \
                                            -Dsonar.projectKey=${repo.projectKey} \
                                            -Dsonar.projectName=${repo.name} \
                                            -Dsonar.sources=. \
                                            -Dsonar.php.file.suffixes=php,php3,php4,php5,phtml,inc,ctp \
                                            -Dsonar.exclusions=vendor/**
                                    """
                                }
                            }
                        }

                        stage("Quality Gate - ${repo.name}") {
                            timeout(time: 5, unit: 'MINUTES') {
                                echo "🔍 Waiting for Quality Gate result for ${repo.name}..."
                                def qg = waitForQualityGate(abortPipeline: false)
                                if (qg.status != 'OK') {
                                    echo "❌ Quality Gate failed for ${repo.name}: ${qg.status}"
                                    currentBuild.result = 'UNSTABLE'
                                } else {
                                    echo "✅ Quality Gate passed for ${repo.name}!"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
