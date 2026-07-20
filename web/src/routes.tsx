import type { RouteObject } from 'react-router'
import { RootLayout } from '@/components/layout/RootLayout'
import { CharactersPage } from '@/pages/CharactersPage'
import { CampaignsPage } from '@/pages/CampaignsPage'

/**
 * Route table. Signed-in pages render inside the RootLayout shell via its <Outlet/>. Campaigns is
 * the landing page (`/`, US2); Characters lives on its own route (`/characters`, US1).
 *
 * Scaffold state (T020): routes are not yet gated — T021 wraps them in a `RequireAuth` layout route
 * (login / no-access / spinner) once the session context exists. Per-campaign, combat, and profile
 * routes arrive with their stories.
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, element: <CampaignsPage /> },
      { path: 'characters', element: <CharactersPage /> },
    ],
  },
]
