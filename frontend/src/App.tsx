import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError, OFFLINE, onUnauthorized, type User } from './api'
import YoyuSplash from './components/YoyuSplash'
import { useOnline } from './hooks'
import Nav from './Nav'
import { navigate, useLocation } from './router'
import HistoryScreen from './screens/HistoryScreen'
import HomeScreen from './screens/HomeScreen'
import JobsScreen from './screens/JobsScreen'
import { WelcomeScreen } from './screens/AuthScreens'
import { legalScreen, PUBLIC_PATHS, signedOutScreen } from './publicRoutes'
import PayScreen from './screens/PayScreen'
import SettingsScreen from './screens/SettingsScreen'
import ShiftScreen from './screens/ShiftScreen'
import VacationsScreen from './screens/VacationsScreen'
import { LoadFailed, Link, Loading, Notice, Screen } from './ui'
import { BRAND } from './brand'

const EDIT_SHIFT = /^\/shifts\/([0-9a-f-]{36})\/edit$/

export default function App() {
  const online = useOnline()
  const [splash, setSplash] = useState(true)
  const endSplash = useCallback(() => setSplash(false), [])

  return (
    <>
      {splash && <YoyuSplash onDone={endSplash} />}
      {!online && (
        <div className="offline">
          <Notice tone="info">{OFFLINE}</Notice>
        </div>
      )}
      <Routes />
    </>
  )
}

function Routes() {
  const { href, path } = useLocation()
  const [user, setUser] = useState<User | null>()
  const [bootError, setBootError] = useState<unknown>()
  const [attempt, setAttempt] = useState(0)

  const signedIn = useCallback((account: User) => api.jobs().then(
    (jobs) => (jobs.length === 0 ? '/jobs?welcome' : '/'),
    (error: unknown) => {
      if (error instanceof ApiError && error.status === 401) throw error
      return '/'
    },
  ).then((to) => {
    navigate(to, { replace: true })
    setUser(account)
  }), [])

  const current = useRef(user)

  useEffect(() => {
    current.current = user
  }, [user])

  useEffect(() => {
    onUnauthorized(() => {
      const wasSignedIn = !!current.current
      setUser(null)
      if (wasSignedIn || !PUBLIC_PATHS.includes(window.location.pathname)) {
        navigate('/login', { replace: true })
      }
    })
  }, [])

  useEffect(() => {
    api.me().then(setUser, (error: unknown) => {
      if (!(error instanceof ApiError && error.status === 401)) {
        setBootError(error)
      }
    })
  }, [attempt])

  useEffect(() => {
    if (user && (path === '/login' || path === '/register')) {
      navigate('/', { replace: true })
    }
  }, [user, path])

  function signedOut() {
    setUser(null)
    navigate('/login', { replace: true })
  }

  const legal = legalScreen(path)

  if (user === undefined) {
    return legal ?? (
      <Screen title={BRAND.name}>
        {bootError ? (
          <LoadFailed
            error={bootError}
            onRetry={() => {
              setBootError(undefined)
              setAttempt((value) => value + 1)
            }}
          />
        ) : (
          <Loading />
        )}
      </Screen>
    )
  }

  if (user === null) {
    return signedOutScreen(path, href, signedIn)
  }

  if (user.username === null || user.fullName === null) {
    return legal ?? <WelcomeScreen user={user} onSignedIn={signedIn} />
  }

  return (
    <div className="app">
      <div key={href}>{route(path, user, signedOut, setUser)}</div>
      <Nav path={path} />
    </div>
  )
}

function route(path: string, user: User, signedOut: () => void, updated: (user: User) => void) {
  const edit = EDIT_SHIFT.exec(path)
  if (edit) {
    return <ShiftScreen shiftId={edit[1]} />
  }
  switch (path) {
    case '/':
    case '/login':
    case '/register':
    case '/welcome':
      return <HomeScreen />
    case '/history':
      return <HistoryScreen />
    case '/shifts/new':
      return <ShiftScreen />
    case '/jobs':
      return <JobsScreen />
    case '/vacations':
      return <VacationsScreen />
    case '/pay':
      return <PayScreen />
    case '/settings':
      return <SettingsScreen user={user} onSignedOut={signedOut} onUpdated={updated} />
    case '/privacy':
    case '/terms':
      return legalScreen(path)
    default:
      return (
        <Screen title="Page not found">
          <Link to="/" className="button">
            Go home
          </Link>
        </Screen>
      )
  }
}
