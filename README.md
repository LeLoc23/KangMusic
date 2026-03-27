# KangMusic - Emotion-Based Music Web Application

KangMusic is a dynamic, full-stack web application designed to bring users a seamless media entertainment experience. Built with a robust Spring Boot backend, it features a secure authentication system, automated email recovery, and a media library that categorizes music and videos based on emotions.

## Key Features
* **Secure Authentication:** User registration and login with BCrypt password encryption via Spring Security.
* **Role-Based Access Control:** Distinct privileges for `ROLE_USER` (streaming media) and `ROLE_ADMIN` (managing the media library).
* **Automated Password Recovery:** Integration with Spring Mail to send secure, token-based password reset links to registered emails.
* **Smart Media Library:** Supports both Audio (`.mp3`) and Video (`.mp4`) playback directly in the browser.
* **Emotion Filtering (Upcoming):** Categorizes and filters music based on user emotions (e.g., Happy, Chill, Energetic).

## Technology Stack
* **Backend:** Java 21, Spring Boot (Web, Data JPA, Security, Mail)
* **Frontend:** HTML5, CSS3, Thymeleaf (Template Engine)
* **Database:** H2 In-Memory Database (for rapid development and testing)
* **Build Tool:** Maven

## How to Run the Project

1. Prerequisites
Make sure you have the following installed on your machine:
* [Java Development Kit (JDK) 21](https://www.oracle.com/java/technologies/downloads/) or higher.
* An IDE like Visual Studio Code, IntelliJ IDEA, or Eclipse.
* A valid Gmail account with an **App Password** generated (for the forgot password feature).

2. Configuration
Before running the application, you need to configure the email server.
1. Navigate to `src/main/resources/application.properties`.
2. Update the Spring Mail configuration with your Gmail credentials:
   ```properties
   spring.mail.username=your_email@gmail.com
   spring.mail.password=your_16_character_app_password

3. Build and Run
Open the project in your preferred IDE.

Clean the workspace and let Maven download the required dependencies.

Run the main class located at src/main/java/com/musicapp/KangmusicApplication.java.

The application will start on port 8080.

4. Accessing the Application

Open your web browser and go to: http://localhost:8080

H2 Database Console: http://localhost:8080/h2-console   
