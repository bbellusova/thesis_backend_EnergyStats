# Energy Stats

This project implements the backend of a web application for monitoring energy production and consumption of a household with photovoltaics.
The frontend implementation is available at: https://github.com/bbellusova/thesis_frontend_EnergyStats


## How to run the app
Requirements: Java 21
* clone this repository
* configure .env (see .env config below)
* chmod +x ./gradlew - add executable rights to gradlew
* dos2unix gradlew - convert line endings to LF (files have CRLF line endings)
* ./gradlew run - run the app
* API docs should now be available at http://localhost:8080/energyStatsApi/swagger-ui/index.html

## .env config
The following environment variables have to be configured:

OPENCAGE_APIKEY
DATABASE_USER
DATABASE_PASS
DATABASE_URL
DATABASE_DRIVER
JASYPT_ENCRYPTOR_PASSWORD
SERVER_SERVLET_SESSION_COOKIE_SECURE=true

## License

Energy Stats: Photovoltaic energy generation and energy consumption monitoring application for households. Copyright (C) 2026 Beáta Bellušová This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.

Contact: bellusova.beata@gmail.com

<img src="https://fit.cvut.cz/static/images/fit-cvut-logo-en.svg" alt="FIT CTU logo" height="200">

This software was developed with the support of the **Faculty of Information Technology, Czech Technical University in Prague**.
For more information, visit [fit.cvut.cz](https://fit.cvut.cz).


