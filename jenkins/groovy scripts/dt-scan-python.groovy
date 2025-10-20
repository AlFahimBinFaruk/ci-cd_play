pipeline {
    agent { label 'master-node' }

    environment {
        GITLAB_CREDENTIALS_ID = 'gitlab-root-user-pass-creds-for-http'
        repositoryUrl = 'https://github.com/AlFahimBinFaruk/TicketTorque_Backend.git'
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
                        # python3 -m venv venv
                        # . venv/bin/activate
                        # pip install -r requirements.txt

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
                    projectName: 'demo-python-app',
                    projectVersion: '1.0.0',
                    synchronous: true
                )
            }
        }
    }
}

