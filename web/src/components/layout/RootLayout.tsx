import { Text } from '@chakra-ui/react'
import { NavLink, Outlet } from 'react-router'
import { AppShell, ColorModeButton, Navbar } from '@rauboti/ui'

/**
 * App shell: the centred column, "skip to content" link, and footer frame come from
 * @rauboti/ui's AppShell; the top Navbar holds the brand, the primary nav links, and the
 * colour-mode toggle. Routed pages render through the <Outlet/>.
 *
 * Scaffold state (T020): labels are literal English strings — T022 swaps them for `t(...)` — and
 * the Navbar actions hold only the colour-mode toggle. T021 adds the auth guard (RequireAuth) and
 * the @rauboti/ui UserMenu (sign-out + profile) once the session context exists.
 */
export const RootLayout = () => (
  <AppShell
    nav={
      <Navbar brand="Tome" actions={<ColorModeButton />}>
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
