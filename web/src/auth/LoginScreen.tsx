import { Center, Heading, Stack, Text } from '@chakra-ui/react'
import { Button, Callout } from '@rauboti/ui'
import { useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { LOGIN_PATH } from '@/api/client'

/**
 * Unauthenticated landing: a "Sign in with Hive" action linking to the BFF's `/auth/login` (a
 * full-page nav that 302s to Hive, so not a client route). A Hive-unreachable callback returns
 * `?error=signin_unavailable` (AuthController marker), shown as a Callout. English until
 * sign-in — the user's locale is only known after.
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
