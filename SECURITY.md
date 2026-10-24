# Security policy

Yoyū handles sign-in and work-hour data for students, so security reports are welcome.

## Reporting a vulnerability

Please do not open a public issue for a security problem. Email **yoyu.notifications@gmail.com** with:

- what you found and where (a URL or file),
- steps to reproduce it,
- the impact you expect.

You will get a reply as soon as I can manage; this is a personal project maintained by one person, so there is no guaranteed response time or bug bounty.

## Scope

In scope: this repository's code and the application at https://yoyu.azurewebsites.net, limited to your own test account. Please do not run load or denial-of-service tests (the app runs on free hosting tiers), try to access other people's accounts or data, or send mail to addresses you do not own.

Out of scope: the hosting providers (Azure, Neon), Google, LINE and Gmail themselves.

## How secrets are handled

Every secret comes from environment variables and none are committed. `.env.example` lists the variables with empty or obviously local-only values. The `OTP_SECRET` in `docker-compose.yml` is a local-development placeholder, and the Compose stack binds to `127.0.0.1` only; never reuse either in production.
