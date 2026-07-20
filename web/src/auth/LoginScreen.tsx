import { Center, Heading, Stack, Text } from '@chakra-ui/react'
import { Button, Callout } from '@rauboti/ui'
import { useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { LOGIN_PATH } from '@/api/client'

/**
 * Unauthenticated landing: a single "Sign in with Hive" action that navigates to the BFF's
 * `/auth/login` (a full-page nav — it 302s to Hive, so it can't be a client route). If Hive is
 * unreachable the OAuth callback bounces back with `?error=signin_unavailable`, surfaced here as a
 * Callout (matches the AuthController marker, T011). English until sign-in — the user's locale is
 * only known after.
 */
export const LoginScreen = () => {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const hiveUnavailable = params.get('error') === 'signin_unavailable'

  return (
    <Center minH="100dvh" px="4">
      <Stack gap="6" maxW="sm" w="full" textAlign="center">
        <Stack gap="2">
          <Heading size="2xl">{t('app.name')}</Heading>
          <Text color="text.muted">{t('auth.tagline')}</Text>
        </Stack>
        {hiveUnavailable && (
          <Callout status="error">{t('auth.signinUnavailable')}</Callout>
        )}
        <Button asChild size="lg" width="full">
          <a href={LOGIN_PATH}>{t('auth.signIn')}</a>
        </Button>
      </Stack>
    </Center>
  )
}
