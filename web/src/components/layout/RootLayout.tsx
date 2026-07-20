import { Box, Text } from '@chakra-ui/react'
import { NavLink, Outlet } from 'react-router'
import { AppShell, ColorModeButton, Navbar, UserMenu } from '@rauboti/ui'
import { useTranslation } from 'react-i18next'
import { useSession } from '@/auth/SessionContext'

/**
 * App shell: the centred column, "skip to content" link, and footer frame come from @rauboti/ui's
 * AppShell; the top Navbar holds the brand, primary nav links, the colour-mode toggle, and the
 * @rauboti/ui UserMenu (name + sign-out) — a dropdown on desktop, repeated inline in the mobile
 * drawer. Session data + sign-out come from `useSession`; RootLayout only renders behind RequireAuth,
 * so the user is always present. Routed pages render through the <Outlet/>.
 *
 */
export const RootLayout = () => {
  const { t } = useTranslation()
  const { user, signOut } = useSession()
  const onSignOut = () => void signOut()

  const userMenu = (inline: boolean) => (
    <UserMenu
      name={user?.displayName}
      onSignOut={onSignOut}
      signOutLabel={t('auth.signOut')}
      inline={inline}
    />
  )

  return (
    <AppShell
      nav={
        <Navbar
          brand={t('app.name')}
          actions={
            <>
              <ColorModeButton />
              <Box hideBelow="md">{userMenu(false)}</Box>
            </>
          }
          drawerExtra={userMenu(true)}
        >
          <Navbar.Item asChild>
            <NavLink to="/" end>
              {t('nav.campaigns')}
            </NavLink>
          </Navbar.Item>
          <Navbar.Item asChild>
            <NavLink to="/characters">{t('nav.characters')}</NavLink>
          </Navbar.Item>
        </Navbar>
      }
      footer={
        <Text color="text.muted" fontSize="sm">
          {t('app.name')}
        </Text>
      }
    >
      <Outlet />
    </AppShell>
  )
}
