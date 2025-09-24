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
                    [name: 'root/test-one', projectKey: 'sonar-test-one', branch: 'main']
                    ]
                    // settings -> system configurations -> tools => and then add this.
                    def scannerHome = tool 'sonar-scanner1'

                    repos.each { repo ->
                        stage("SonarQube Scan - ${repo.name}") {
                            dir("${repo.name}") {
                                git branch: repo.branch,
                                    credentialsId: "${GITLAB_CREDENTIALS_ID}",
                                    // here we are using the http to clone repo.
                                    url: "http://gitlab.devops.com:8082/${repo.name}.git"

                                // settings -> system configurations -> system => and then add this.
                                withSonarQubeEnv('my-sonarqube-server-url') {
                                    sh """
                                        ${scannerHome}/bin/sonar-scanner \
                                            -Dsonar.projectKey=${repo.projectKey} \
                                            -Dsonar.projectName=${repo.name} \
                                            -Dsonar.sources=. \
                                            #-Dsonar.php.file.suffixes=php,php3,php4,php5,phtml,inc,ctp \
                                            -Dsonar.exclusions=vendor/**
                                    """
                                }
                            }
                        }

                        stage("Quality Gate - ${repo.name}") {
                            timeout(time: 2, unit: 'MINUTES') {
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

// install sonarqube scanner in jenkins.
// generate sonarqube token from admin panel of sonarqube.

// configure webhook
// settings -> system configurations -> system -> sonarqube -> advance -> select an webhook secret
// then goto sonarqube -> administration -> configuration -> webhooks -> add webhook webhook url will be "http://172.16.218.42:8080/sonarqube-webhook/"
// here you have to use direct laptop id instead of localhost as sonarqube is running in docker container. the secret can be same as sonarqube access token.

// used token=> sqa_b3e8b8f142a86e29382a3e82899b46ca9a34dc19
