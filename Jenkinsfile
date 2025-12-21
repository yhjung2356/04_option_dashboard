pipeline {
    agent any
    
    tools {
        maven 'Maven 3.9'
        jdk 'JDK 17'
    }
    
    environment {
        APP_NAME = 'futures-options-dashboard'
        JAR_FILE = 'target/futures-options-dashboard-1.0.0.jar'
        DEPLOY_DIR = 'C:\\deploy\\futures-dashboard'
    }
    
    stages {
        stage('Checkout') {
            steps {
                echo '소스 코드 체크아웃...'
                git branch: 'main', 
                    url: 'https://github.com/your-repo/futures-options-dashboard.git'
            }
        }
        
        stage('Build') {
            steps {
                echo 'Maven 빌드 시작...'
                bat 'mvnw clean package -DskipTests'
            }
        }
        
        stage('Test') {
            steps {
                echo '테스트 실행...'
                bat 'mvnw test'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }
        
        stage('Stop Old Process') {
            steps {
                echo '기존 프로세스 종료...'
                script {
                    bat '''
                        for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
                            taskkill /F /PID %%a
                        )
                    '''
                }
            }
        }
        
        stage('Deploy') {
            steps {
                echo '애플리케이션 배포...'
                bat """
                    if not exist ${DEPLOY_DIR} mkdir ${DEPLOY_DIR}
                    copy /Y ${JAR_FILE} ${DEPLOY_DIR}\\${APP_NAME}.jar
                """
            }
        }
        
        stage('Start Application') {
            steps {
                echo '애플리케이션 시작...'
                bat """
                    cd ${DEPLOY_DIR}
                    start "Futures Dashboard" java -jar ${APP_NAME}.jar
                """
            }
        }
        
        stage('Health Check') {
            steps {
                echo '헬스 체크...'
                sleep time: 10, unit: 'SECONDS'
                script {
                    try {
                        bat 'curl http://localhost:8080'
                        echo '애플리케이션 정상 구동 확인!'
                    } catch (Exception e) {
                        echo '헬스 체크 실패!'
                        error('애플리케이션이 정상적으로 시작되지 않았습니다.')
                    }
                }
            }
        }
    }
    
    post {
        success {
            echo '========================================='
            echo '  빌드 및 배포 성공! 🎉'
            echo '  http://localhost:8080'
            echo '========================================='
            
            // 슬랙 알림 (선택)
            // slackSend(
            //     color: 'good',
            //     message: "배포 성공: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            // )
        }
        
        failure {
            echo '========================================='
            echo '  빌드 또는 배포 실패 ❌'
            echo '========================================='
            
            // 슬랙 알림 (선택)
            // slackSend(
            //     color: 'danger',
            //     message: "배포 실패: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            // )
        }
        
        always {
            echo '빌드 완료. 워크스페이스 정리...'
            cleanWs()
        }
    }
}
