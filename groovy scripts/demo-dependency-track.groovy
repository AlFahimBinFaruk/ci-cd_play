pipeline {
    agent { label 'master-node' }

    environment {
        GITLAB_CREDENTIALS_ID = 'gitlab-root-user-pass-creds-for-http'
        repositoryUrl = 'http://172.16.229.70:8082/devops/demo-node-app.git'
        repositoryBranch = 'main'
        DT_API_TOKEN = credentials('DT_API_TOKEN')
        DT_API_URL = 'http://172.16.229.70:8085'
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
                        # Ensure project directory is safe
                        chmod -R 755 $(pwd)
                        git config --global --add safe.directory $(pwd)

                        # Install CycloneDX NPM package locally
                        npm install --save-dev @cyclonedx/bom

                        # Generate SBOM in JSON format
                        npx cyclonedx-bom -o sbom.json

                        # Verify SBOM
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

