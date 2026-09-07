# Copy to local-config.ps1 (ignored by Git). Use PowerShell 7.2 or newer.
# This script returns configuration; do not assign secrets to $env here.
@{
    JAVA_HOME = 'D:\JDK21'
    MAVEN_HOME = 'F:\apache-maven-3.8.8' # Build only; Node.js/npm must be on PATH.
    DB_URL = 'jdbc:mysql://127.0.0.1:3306/life1000?characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai'
    DB_USERNAME = 'REPLACE_ME'
    DB_PASSWORD = 'REPLACE_ME'
    LIFE1000_USERNAME = 'REPLACE_ME'
    LIFE1000_PASSWORD = 'REPLACE_ME'
    # Generate once: [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    LIFE1000_JWT_SECRET = 'REPLACE_ME'
    # Absolute path to your EXISTING upload directory. Do not move existing attachments.
    LIFE1000_UPLOAD_DIRECTORY = 'D:\code\myself\Life1000\backend\uploads'
    NGINX_HOME = 'D:\nginx'
}
