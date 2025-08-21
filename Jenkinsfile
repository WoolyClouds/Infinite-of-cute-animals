pipeline {
    agent any

    environment {
        APP_NAME = 'infinite-of-cute-animals'
        DEPLOY_PATH = '/home/ec2-user/app'
    }

    stages {
        stage('🔍 Checkout') {
            steps {
                echo '📥 소스코드 체크아웃 중...'
                checkout scm
            }
        }

        stage('🔧 Build') {
            steps {
                echo '🏗️ 애플리케이션 빌드 중...'
                sh '''
                    # Gradle wrapper 권한 설정
                    chmod +x gradlew

                    # 빌드 실행 (테스트 제외)
                    ./gradlew build -x test
                '''
            }
        }

        stage('📦 Package') {
            steps {
                echo '📦 JAR 파일 패키징 중...'
                sh '''
                    # JAR 파일 생성 확인
                    ls -la build/libs/

                    # 배포 디렉토리 생성
                    mkdir -p ${DEPLOY_PATH}

                    # 기존 JAR 백업
                    if [ -f "${DEPLOY_PATH}/${APP_NAME}.jar" ]; then
                        cp ${DEPLOY_PATH}/${APP_NAME}.jar ${DEPLOY_PATH}/${APP_NAME}-backup.jar
                        echo "✅ 기존 JAR 파일 백업 완료"
                    fi

                    # 새로운 JAR 복사 (SNAPSHOT 버전만)
                    cp build/libs/*-SNAPSHOT.jar ${DEPLOY_PATH}/${APP_NAME}.jar
                    echo "✅ 새로운 JAR 파일 배포 준비 완료"
                '''
            }
        }

        stage('🚀 Deploy') {
            steps {
                echo '🚀 애플리케이션 배포 중...'
                sh '''
                    cd ${DEPLOY_PATH}

                    # 기존 프로세스 종료
                    PID=$(pgrep -f "${APP_NAME}.jar" || true)
                    if [ ! -z "$PID" ]; then
                        echo "🛑 기존 애플리케이션 종료 중... (PID: $PID)"
                        kill $PID
                        sleep 10

                        # 강제 종료 확인
                        if pgrep -f "${APP_NAME}.jar"; then
                            echo "⚠️ 강제 종료 실행"
                            pkill -9 -f "${APP_NAME}.jar"
                        fi
                    fi

                    # 새로운 애플리케이션 시작
                    echo "🔄 새로운 애플리케이션 시작 중..."
                    nohup java -jar -Dspring.profiles.active=dev ${APP_NAME}.jar > app.log 2>&1 &

                    # 실행 확인
                    sleep 15
                    if pgrep -f "${APP_NAME}.jar"; then
                        echo "✅ 애플리케이션이 성공적으로 시작되었습니다!"
                        echo "🌐 접속 주소: http://10.0.1.207:8888"
                    else
                        echo "❌ 애플리케이션 시작 실패"
                        echo "📋 로그 확인:"
                        tail -20 app.log
                        exit 1
                    fi
                '''
            }
        }
    }

    post {
        success {
            echo '🎉 배포가 성공적으로 완료되었습니다!'
        }
        failure {
            echo '❌ 배포가 실패했습니다.'
        }
        always {
            echo '🧹 정리 작업 중...'
            // 아티팩트 보관
            archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
        }
    }
}