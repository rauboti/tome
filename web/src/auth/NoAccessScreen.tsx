import { Center, Heading, Stack, Text } from '@chakra-ui/react'
import { Button } from '@rauboti/ui'
import { useSession } from './SessionContext'

/**
 * Shown by the RequireAuth guard when the user is signed in to Hive but has no Tome role (403 on
 * `/api/auth/me`, FR-024). A sibling of [LoginScreen], not a routed page: they *are* authenticated,
 * they just can't use Tome — so the only action is to sign out (e.g. to switch accounts). A Tome
 * role is granted in Hive, out of band. Labels are literal English for now (T022 localizes).
 */
export const NoAccessScreen = () => {
  const { signOut } = useSession()

  return (
    <Center minH="100dvh" px="4">
      <Stack gap="6" maxW="sm" w="full" textAlign="center">
        <Stack gap="2">
          <Heading size="xl">No access to Tome</Heading>
          <Text color="text.muted">
            You are signed in, but your account has not been granted access to
            Tome. Ask an administrator for a role, then reload.
          </Text>
        </Stack>
        <Button variant="outline" onClick={() => void signOut()}>
          Sign out
        </Button>
      </Stack>
    </Center>
  )
}
