# --- 1. Build Stage ---
FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

# 밖에서 던져준 build-args를 잡는 그물망
ARG VITE_API_URL
# 잡은 값을 환경변수로 설정 (Vite가 빌드할 때 사용)
ENV VITE_API_URL=${VITE_API_URL}
COPY . .
RUN npm run build

# --- 2. Nginx Stage ---
FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html

# React Router 대응 (SPA)
COPY ./nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
