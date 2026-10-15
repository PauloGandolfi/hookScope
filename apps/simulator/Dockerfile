FROM node:24-alpine AS test

WORKDIR /app
COPY package.json ./
COPY src ./src
COPY test ./test
RUN npm test

FROM node:24-alpine

ENV NODE_ENV=production
WORKDIR /app

COPY --chown=node:node package.json ./
COPY --chown=node:node src ./src

USER node

CMD ["npm", "start"]
