// `.env.ts` is generated by the `npm run env` command
import env from './.env';

export const environment = {
  production: true,
  version: env.npm_package_version,
  serverUrl: 'https://giftyou.herokuapp.com',
  localUrl: 'https://giftyou.herokuapp.com',
  defaultLanguage: 'en-US',
  supportedLanguages: ['en-US', 'fr-FR']
};
