/*
To fix
[] repo name fix
[] env fix
[] last scan
*/

pipeline {
    agent { label 'master-node' }
    environment {
        GITLAB_CREDENTIALS_ID = 'gitlab-root-user-pass-creds-for-http'
        SONAR_TOKEN = credentials('sonarqube-token-jenkins-global')
        REGISTRY_URL = 'harbor.devops.com:8083'
        REGISTRY_CREDENTIALS = 'harbor-registry-user-pass-creds'
        IMAGE_NAME = 'testproject/dvwa-master'
        IMAGE_TAG = 'latest'
        DB_HOST = 'alpha-script-db-1'
        DB_PORT = '3306'
        DB_DATABASE = 'dvwa'
        DB_USERNAME = 'dvwa'
        DB_PASSWORD = 'p@ssw0rd'
    }

    stages {
        // Stage 1: Checkout code from GitLab
        stage('Checkout from GitLab') {
            steps {
                git branch: 'main',
                url: 'http://gitlab.devops.com:8082/root/dvwa-master.git',
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
        // stage('Docker Compose Deploy') {
        //     steps {
        //         script {
        //             def BUILD_TAG = "build-${env.BUILD_NUMBER}"
        //             withCredentials([usernamePassword(credentialsId: "${REGISTRY_CREDENTIALS}",
        //             usernameVariable: 'HARBOR_USER',
        //             passwordVariable: 'HARBOR_PASS')]) {
        //                 withEnv([
        //                 "APP_TAG=${BUILD_TAG}",
        //                 "REGISTRY_URL=${REGISTRY_URL}",
        //                 "IMAGE_NAME=${IMAGE_NAME}"
        //             ]) {
        //                     sh """
        //                 echo $HARBOR_PASS | docker login ${REGISTRY_URL} -u $HARBOR_USER --password-stdin
        //                 docker compose pull
        //                 docker compose down
        //                 docker compose up -d
        //                 docker logout ${REGISTRY_URL}
        //                 """
        //             }
        //             }
        //         }
        //     }
        // }

        // // stage 4: gitleaks scan
        // stage('Gitleaks Scan') {
        //     steps {
        //         script {
        //                 sh """
        //                     mkdir -p gitleaks-output

        //                     docker run --rm \\
        //                         -v \$(pwd):/path \\
        //                         ghcr.io/gitleaks/gitleaks:latest detect \\
        //                         --source="/path" \\
        //                         --no-git \\
        //                         --verbose \\
        //                         --report-format json \\
        //                         --report-path=/path/gitleaks-output/gitleaks-report.json \\
        //                         --redact || exit 0
        //                 """
        //         }
        //     }
        // }

        // // stage 5: sonarqube scan
        // stage('SonarQube Scan Stage') {
        //     steps {
        //         script {
        //             def repos = [
        //             [name: 'root/dvwa-master', projectKey: 'sonar-dvwa-master', branch: 'main']
        //             ]
        //             // settings -> system configurations -> tools => and then add this.
        //             def scannerHome = tool 'sonar-scanner1'

        //             repos.each { repo ->
        //                 stage("SonarQube Scan - ${repo.name}") {
        //                     dir("${repo.name}") {
        //                         git branch: repo.branch,
        //                             credentialsId: "${GITLAB_CREDENTIALS_ID}",
        //                             // here we are using the http to clone repo.
        //                             url: "http://gitlab.devops.com:8082/${repo.name}.git"

        //                         // settings -> system configurations -> system => and then add this.
        //                         withSonarQubeEnv('my-sonarqube-server-url') {
        //                             sh """
        //                                 ${scannerHome}/bin/sonar-scanner \
        //                                     -Dsonar.projectKey=${repo.projectKey} \
        //                                     -Dsonar.projectName=${repo.name} \
        //                                     -Dsonar.sources=. \
        //                                     #-Dsonar.php.file.suffixes=php,php3,php4,php5,phtml,inc,ctp \
        //                                     -Dsonar.exclusions=vendor/**
        //                             """
        //                         }
        //                     }
        //                 }

        //                 stage("Quality Gate - ${repo.name}") {
        //                     timeout(time: 10, unit: 'MINUTES') {
        //                         echo "🔍 Waiting for Quality Gate result for ${repo.name}..."
        //                         def qg = waitForQualityGate(abortPipeline: false)
        //                         if (qg.status != 'OK') {
        //                             echo "❌ Quality Gate failed for ${repo.name}: ${qg.status}"
        //                             currentBuild.result = 'UNSTABLE'
        //                         } else {
        //                             echo "✅ Quality Gate passed for ${repo.name}!"
        //                         }
        //                     }
        //                 }
        //             }
        //         }
        //     }
        // }

        // stage 6: run tests
        // stage('Testing') {
        //     steps {
        //         script {
        //             def BUILD_TAG = "build-${env.BUILD_NUMBER}"
        //             def dockerImageName = "${REGISTRY_URL}/${IMAGE_NAME}"
        //             def dockerContainerName = 'dvwa-test'

        //             sh """
        //                 echo 'Running Laravel tests'
        //                 docker run --rm \
        //                 --name ${dockerContainerName}-${BUILD_TAG}-test \
        //                 --env-file ./src/.env.testing \
        //                 ${dockerImageName}:${BUILD_TAG} \
        //                 bash -c "composer install -q --no-ansi --no-interaction --no-scripts --no-progress --prefer-dist && php artisan test"
        //             """
        //         }
        //     }
        // }

        // stage 7: human approval
        // stage('Approve DB Migration') {
        //     steps {
        //         timeout(time: 300, unit: 'SECONDS') {
        //             input 'Do you want to proceed to the DB migration?'
        //         }
        //     }
        // }

        // stage 8: db migration
        stage('DB Migration') {
            steps {
                script {
                    def BUILD_TAG = "build-${env.BUILD_NUMBER}"
                    def dockerImageName = "${REGISTRY_URL}/${IMAGE_NAME}"

                    sh """
                        docker run --rm \
                        -e "DB_HOST=${DB_HOST}" \
                        -e "DB_PORT=${DB_PORT}" \
                        -e "DB_DATABASE=${DB_DATABASE}" \
                        -e "DB_USERNAME=${DB_USERNAME}" \
                        -e "DB_PASSWORD=${DB_PASSWORD}" \
                        ${dockerImageName}:${BUILD_TAG} \
                        php artisan --force migrate
                    """
                }
            }
        }
    }
}
