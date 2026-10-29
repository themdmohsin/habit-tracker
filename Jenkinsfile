// Runs on Windows (bat) or Linux (sh) using the Maven Wrapper; no Maven install needed.
def mvnw(String args) {
    if (isUnix()) {
        sh "./mvnw -B ${args}"
    } else {
        bat "mvnw.cmd -B ${args}"
    }
}

pipeline {
    agent any

    triggers {
        pollSCM('* * * * *')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                mvnw('test')
            }
        }

        stage('Build') {
            steps {
                mvnw('package -DskipTests')
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/habit-tracker.jar', fingerprint: true
            }
        }
    }

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
        }
        success {
            echo 'Pipeline succeeded: tests passed and habit-tracker.jar was archived.'
        }
        failure {
            echo 'Pipeline failed: check the Test or Build stage output above.'
        }
    }
}
