import { Box, Text } from '@chakra-ui/react'
import { NavLink, Outlet } from 'react-router'
import { AppShell, ColorModeButton, Navbar, UserMenu } from '@rauboti/ui'
import { useSession } from '@/auth/SessionContext'

/**
 * App shell: the centred column, "skip to content" link, and footer frame come from @rauboti/ui's
 * AppShell; the top Navbar holds the brand, primary nav links, the colour-mode toggle, and the
 * @rauboti/ui UserMenu (name + sign-out) — a dropdown on desktop, repeated inline in the mobile
 * drawer. Session data + sign-out come from `useSession`; RootLayout only renders behind RequireAuth,
 * so the user is always present. Routed pages render through the <Outlet/>.
 *
 * Labels are literal English for now — T022 swaps them for `t(...)`.
 */
export const RootLayout = () => {
  const { user, signOut } = useSession()
  const onSignOut = () => void signOut()

  const userMenu = (inline: boolean) => (
    <UserMenu
      name={user?.displayName}
      onSignOut={onSignOut}
      signOutLabel="Sign out"
      inline={inline}
    />
  )

  return (
    <AppShell
      nav={
        <Navbar
          brand="Tome"
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
              Campaigns
            </NavLink>
          </Navbar.Item>
          <Navbar.Item asChild>
            <NavLink to="/characters">Characters</NavLink>
          </Navbar.Item>
        </Navbar>
      }
      footer={
        <Text color="text.muted" fontSize="sm">
          Tome
        </Text>
      }
    >
      <Outlet />
    </AppShell>
  )
}
