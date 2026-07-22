import type { RouteObject } from 'react-router'
import { RequireAuth } from '@/auth/RequireAuth'
import { RootLayout } from '@/components/layout/RootLayout'
import { CharactersPage } from '@/pages/CharactersPage'
import { CharacterSheetPage } from '@/pages/CharacterSheetPage'
import { CampaignsPage } from '@/pages/CampaignsPage'

/**
 * Route table. RequireAuth gates the app (login screen when signed out, no-access screen when
 * signed in without a Tome role); signed-in pages render inside the RootLayout shell via its
 * <Outlet/>. Campaigns is the landing page (`/`, US2); Characters lists on `/characters` and a
 * single character's sheet opens at `/characters/:characterId` (US1). Per-campaign, combat, and
 * profile routes arrive with their stories.
 */
export const routes: RouteObject[] = [
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/',
        element: <RootLayout />,
        children: [
          { index: true, element: <CampaignsPage /> },
          { path: 'characters', element: <CharactersPage /> },
          { path: 'characters/:characterId', element: <CharacterSheetPage /> },
        ],
      },
    ],
  },
]
