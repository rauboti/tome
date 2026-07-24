import { Center, Spinner } from '@chakra-ui/react'
import { Outlet } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useSession } from './SessionContext'
import { LoginScreen } from './LoginScreen'
import { NoAccessScreen } from './NoAccessScreen'

/**
 * Route guard (pathless layout route in `routes.tsx`), branching on `useSession()` status: a spinner
 * while the `/api/auth/me` probe is in flight, the login screen when unauthenticated, the no-access
 * screen when signed in without a Tome role (FR-024), else the routed app (`<Outlet/>`).
 */
export const RequireAuth = () => {
  const { t } = useTranslation()
  const { status } = useSession()

  if (status === 'loading') {
    return (
      <Center
        minH="100dvh"
        role="status"
        aria-label={t('auth.checkingSession')}
      >
        <Spinner />
      </Center>
    )
  }

  if (status === 'unauthenticated') return <LoginScreen />
  if (status === 'noAccess') return <NoAccessScreen />

  return <Outlet />
}
