FROM node:22-alpine

LABEL authors="Radu"

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

COPY . .

EXPOSE 4200

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh && sed -i 's/\r//' /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
