import { Center, Heading, Stack, Text } from '@chakra-ui/react'
import { Button, Callout } from '@rauboti/ui'
import { useSearchParams } from 'react-router'
import { LOGIN_PATH } from './SessionContext'

/**
 * Unauthenticated landing: a single "Sign in with Hive" action that navigates to the BFF's
 * `/auth/login` (a full-page nav — it 302s to Hive, so it can't be a client route). If Hive is
 * unreachable the OAuth callback bounces back with `?error=signin_unavailable`, surfaced here as a
 * Callout (matches the AuthController marker, T011).
 *
 * Labels are literal English for now — T022 localizes them (this screen shows before the user's
 * locale is known anyway).
 */
export const LoginScreen = () => {
  const [params] = useSearchParams()
  const hiveUnavailable = params.get('error') === 'signin_unavailable'

  return (
    <Center minH="100dvh" px="4">
      <Stack gap="6" maxW="sm" w="full" textAlign="center">
        <Stack gap="2">
          <Heading size="2xl">Tome</Heading>
          <Text color="text.muted">Run your tabletop campaign.</Text>
        </Stack>
        {hiveUnavailable && (
          <Callout status="error">
            Sign-in is unavailable right now. Please try again.
          </Callout>
        )}
        <Button asChild size="lg" width="full">
          <a href={LOGIN_PATH}>Sign in with Hive</a>
        </Button>
      </Stack>
    </Center>
  )
}
