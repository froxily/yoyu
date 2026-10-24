import { Link } from './ui'

const ICONS = {
  home: 'M3 10.5 12 3l9 7.5M5 9v12h5v-6h4v6h5V9',
  history: 'M4 5h16v16H4zM4 10h16M9 3v4M15 3v4',
  add: 'M12 5v14M5 12h14',
  pay: 'M6 3l6 8 6-8M12 11v10M7 12h10M7 16h10',
  settings: 'M4 7h9M17 7h3M4 17h3M11 17h9M15 5v4M9 15v4',
}

type IconName = keyof typeof ICONS

const ITEMS: { to: string; label: string; icon: IconName; match: (path: string) => boolean }[] = [
  { to: '/', label: 'Home', icon: 'home', match: (path) => path === '/' },
  { to: '/history', label: 'History', icon: 'history', match: (path) => path.startsWith('/history') },
  { to: '/shifts/new', label: 'Add shift', icon: 'add', match: (path) => path.startsWith('/shifts') },
  { to: '/pay', label: 'Pay', icon: 'pay', match: (path) => path.startsWith('/pay') },
  {
    to: '/settings',
    label: 'Settings',
    icon: 'settings',
    match: (path) => ['/settings', '/jobs', '/vacations'].some((prefix) => path.startsWith(prefix)),
  },
]

export default function Nav({ path }: { path: string }) {
  return (
    <nav className="nav" aria-label="Main">
      {ITEMS.map((item) => (
        <Link
          key={item.to}
          to={item.to}
          className={`nav-item${item.icon === 'add' ? ' nav-add' : ''}`}
          aria-current={item.match(path) ? 'page' : undefined}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d={ICONS[item.icon]} />
          </svg>
          <span>{item.label}</span>
        </Link>
      ))}
    </nav>
  )
}
