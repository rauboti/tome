import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/**
 * The MSW request-mocking server for the test environment. Its lifecycle (listen / resetHandlers /
 * close) is driven from `src/test/setup.ts`. Tests override handlers per-case with `server.use(...)`.
 */
export const server = setupServer(...handlers)
