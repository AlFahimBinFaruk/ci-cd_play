pipeline {
    agent { label 'master-node' }

    environment {
        GITLAB_CREDENTIALS_ID = 'gitlab-root-user-pass-creds-for-http'
        repositoryUrl = 'http://gitlab.devops.com:8082/root/demo-node-app.git'
        repositoryBranch = 'main'
        DT_API_TOKEN = credentials('DT_API_TOKEN')
        DT_API_URL = 'http://localhost:8085'
    }

    stages {
        // 1️⃣ Git Checkout
        stage('Git Checkout') {
            steps {
                git branch: "${repositoryBranch}",
                    credentialsId: "${GITLAB_CREDENTIALS_ID}",
                    url: "${repositoryUrl}"
            }
        }

        // 2️⃣ Install CycloneDX NPM & Generate SBOM
        stage('Generate SBOM') {
            steps {
                script {
                    sh '''
                        chmod -R 755 $(pwd)
                        git config --global --add safe.directory $(pwd)

                        npm install --save-dev @cyclonedx/cyclonedx-npm
                        npx cyclonedx-npm --output-file sbom.json
                        ls -la sbom.json
                    '''
                }
            }
        }

        // 3️⃣ Upload SBOM to Dependency-Track
        stage('Upload SBOM to Dependency-Track') {
            steps {
                dependencyTrackPublisher(
                    artifact: 'sbom.json',
                    autoCreateProjects: true,
                    dependencyTrackApiKey: "${DT_API_TOKEN}",
                    dependencyTrackFrontendUrl: "${DT_API_URL}",
                    dependencyTrackUrl: "${DT_API_URL}",
                    projectName: 'demo-node-app',
                    projectVersion: '1.0.0',
                    synchronous: true
                )
            }
        }
    }
}

/*
user:admin
pass:admin1

1. install the "OWASP Dependency-Track Plugin" plugin in jenkins.
2. goto: depencency track -> administration -> access maangement -> teams and create a new team name "jenkins" generate the api key and add it under "DT_API_TOKEN" in jenkins credentials as secret text. and give this "jenkins" team all the persmissions.
*/
