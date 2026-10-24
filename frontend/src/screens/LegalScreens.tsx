import type { ReactNode } from 'react'
import { BRAND } from '../brand'
import { goBack } from '../router'
import { LegalLinks, Link, Screen } from '../ui'

export const LEGAL_UPDATED = '23 October 2026'

const NAME = BRAND.name

const GOOGLE_USER_DATA_POLICY = 'https://developers.google.com/terms/api-services-user-data-policy'

const GOOGLE_CONNECTIONS = 'https://myaccount.google.com/connections'

function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  return <Screen title={title} action={<button type="button" className="button button-small" onClick={() => goBack('/')}>Back</button>}>
    <article className="card legal">
      <p className="muted small">Last updated {LEGAL_UPDATED}</p>
      {children}
    </article>
    <LegalLinks />
  </Screen>
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return <section><h2>{title}</h2>{children}</section>
}

function Contact() {
  return <a href={`mailto:${BRAND.contactEmail}`}>{BRAND.contactEmail}</a>
}

function External({ href, children }: { href: string; children: ReactNode }) {
  return <a href={href} target="_blank" rel="noreferrer">{children}</a>
}

export function PrivacyScreen() {
  return <LegalPage title="Privacy Policy">
    <p>
      {NAME} is a free tool that helps international students in Japan keep their part-time work within the hours their
      permission allows, and estimate their pay. It is run by an individual developer. This policy explains what {NAME} collects,
      why, where it is kept and how to delete it. Questions: <Contact />.
    </p>
    <ul className="bullets">
      <li>{NAME} collects only what it needs to run your account and do the calculations you ask for.</li>
      <li>No ads, no analytics or tracking, and your data is never sold.</li>
      <li>You can delete your account, with your jobs, shifts and vacations, from Settings at any time.</li>
    </ul>

    <Section title="Your account">
      <ul className="bullets">
        <li>The full name and username you choose.</li>
        <li>Your email address, if you add one, and whether you have confirmed it.</li>
        <li>Your password, if you set one. It is stored only as a BCrypt hash, never in readable form, and is never sent to another service.</li>
        <li>The Google or LINE accounts you link: the provider's ID for your account and when you linked it.</li>
        <li>When your account was created and when your email was confirmed.</li>
      </ul>
      <p>Every record also has technical details such as an ID, a version number and when it was created or changed.</p>
    </Section>

    <Section title="Your jobs, shifts and vacations">
      <ul className="bullets">
        <li>Jobs: name, hourly wage, transport allowance, colour, and whether the job is archived.</li>
        <li>Shifts: the job, start and end times, an optional break, and an optional note.</li>
        <li>Long vacation periods: the dates and an optional label.</li>
      </ul>
      <p>
        {NAME} never asks for your residence card, visa, passport, phone number or employer's details. Please don't put them, or
        anyone else's personal information, in notes or labels.
      </p>
    </Section>

    <Section title="Signing in with Google">
      <p>
        When you choose Continue with Google, you ask Google to share your basic profile with {NAME} (the <code>openid</code>,{' '}
        <code>email</code> and <code>profile</code> permissions): your Google account ID, your email address and whether Google has
        verified it, your name and your profile picture.
      </p>
      <ul className="bullets">
        <li>Your Google account ID is stored so {NAME} can recognise you when you sign in again.</li>
        <li>
          When you create an account with Google, your verified Google email becomes your account email. If another {NAME} account
          already uses that email, you are asked to sign in to it and link Google in Settings; accounts are never merged automatically.
        </li>
        <li>Your name only pre-fills the name field when you create your account. You can change it.</li>
        <li>Your profile picture is not used or stored.</li>
      </ul>
      <p>
        {NAME} does not keep Google access tokens, so it cannot read your Gmail, contacts, calendar, Drive or any other Google data.
      </p>
    </Section>

    <Section title="Signing in with LINE">
      <p>
        When you choose Continue with LINE, LINE shares your LINE user ID, display name and profile picture (the <code>openid</code>{' '}
        and <code>profile</code> permissions). {NAME} stores your LINE user ID to recognise you, uses your display name only to
        pre-fill the name field, and does not use or store your picture. {NAME} does not currently ask LINE for your email address.
        It does not keep LINE access tokens, so it cannot see your friends or chats, or send messages.
      </p>
      <p>
        While you finish creating an account after a Google or LINE sign-in, these details are held in a short sign-in session.
        They can be used for 10 minutes, and the session is deleted by an automatic cleanup, normally within an hour.
      </p>
      <p>
        Once linked, your Google or LINE account is used to sign you in and, with a fresh sign-in, to confirm it's you before a
        sensitive change such as deleting your account.
      </p>
    </Section>

    <Section title="Email codes and emails">
      <ul className="bullets">
        <li>
          {NAME} emails you a 6-digit code to create an account with email, to confirm your email, to reset your password, to change
          your email, or to confirm it's you before a sensitive change. Codes are stored only as a keyed hash, with the address
          they were sent to and how many times they were sent and tried. They expire after 10 minutes and are deleted when used.
        </li>
        <li>
          When you sign up with email, the name, username and email you entered, and a hash of your password, are kept with the
          code, and your account is created only once you enter the code.
        </li>
        <li>
          {NAME} also emails you notices about your account: when a password is added or changed, when your email changes (sent to
          the old address), when someone tries to sign up or switch to your address while it already has an account, and when
          someone asks to reset the password of an account that signs in only with Google or LINE.
        </li>
        <li>
          Emails are sent through Google's Gmail service from <Contact />. Gmail keeps a copy of each email sent (the address,
          subject and message) in that mailbox. These copies are not removed automatically when you delete your account.
        </li>
      </ul>
    </Section>

    <Section title="Cookies and storage on your device">
      <ul className="bullets">
        <li>
          <code>SESSION</code> keeps you signed in. It can't be read by scripts and, on the live site, is sent only over HTTPS. A
          signed-in session lasts 30 days after you last use it, or until you sign out. Sessions are stored in {NAME}'s database
          with your account ID, the times they were created and last used, and security details such as when you last confirmed
          it's you.
        </li>
        <li>
          Starting a Google or LINE sign-in creates a short session holding the security values for that sign-in. It expires after
          10 minutes without activity and is deleted by an automatic cleanup, normally within an hour.
        </li>
        <li><code>XSRF-TOKEN</code> protects your requests against cross-site request forgery.</li>
        <li>
          While you confirm a sensitive change through Google or LINE, this browser tab's session storage remembers your account ID
          and which change you were making (and the new email address, if you were changing it). It is ignored after 15 minutes, cleared when you return
          to Settings, and removed when you close the tab.
        </li>
        <li>
          The app's own files are saved on your device so it opens quickly and can be installed. Your jobs, shifts and other
          records are not saved on your device for offline use.
        </li>
        <li>There are no advertising, analytics or tracking cookies or scripts, and no third-party fonts.</li>
      </ul>
    </Section>

    <Section title="Security and abuse protection">
      <ul className="bullets">
        <li>
          Your IP address and the email or username you type into sign-in, sign-up and password recovery are used to limit repeated
          attempts. These counters are kept only in the server's memory for about an hour and are lost when the service restarts.
        </li>
        <li>
          The app logs errors and service events. It does not log IP addresses, the pages you visit, or the passwords and codes you
          enter, though the details of an unexpected error can occasionally include data from the request that caused it. When a
          Google or LINE confirmation is rejected, the log records timing details from the provider's reply, without your name,
          email or account ID. Azure keeps these logs for up to 3 days.
        </li>
        <li>
          Microsoft Azure, which hosts {NAME}, processes connection details such as your IP address to deliver and protect the
          service, under Microsoft's own terms.
        </li>
        <li>
          {NAME} is served only over HTTPS, its database connection is encrypted and certificate-checked, and each account can
          reach only its own data. No online service is perfectly secure, so {NAME} can't guarantee that your data
          will never be exposed.
        </li>
      </ul>
    </Section>

    <Section title="How your data is used">
      <ul className="bullets">
        <li>To create your account, sign you in and keep your account secure.</li>
        <li>To calculate your hours, limits, warnings and pay estimates from what you enter.</li>
        <li>To send the codes and account notices described above.</li>
      </ul>
      <p>{NAME} does not use your data for advertising or marketing and does not sell or rent it.</p>
    </Section>

    <Section title="Services that process your data">
      <ul className="bullets">
        <li>Microsoft Azure hosts the app, in its Malaysia West region (Malaysia).</li>
        <li>Neon hosts the database, on Amazon Web Services in Singapore.</li>
        <li>Google sends {NAME}'s emails through Gmail, and handles Google sign-in if you use it.</li>
        <li>LY Corporation handles LINE sign-in if you use it.</li>
      </ul>
      <p>
        Your data is therefore stored and processed outside Japan. {NAME} shares it with no one else, except where the law
        requires it.
      </p>
    </Section>

    <Section title="Google user data">
      <p>
        {NAME}'s use and transfer of information received from Google APIs adheres to the{' '}
        <External href={GOOGLE_USER_DATA_POLICY}>Google API Services User Data Policy</External>, including the Limited Use
        requirements. {NAME} uses that information only to provide your account: to create it, sign you in, confirm it's you before
        sensitive changes, and send codes and account notices to your account email, as described above.
      </p>
    </Section>

    <Section title="How long data is kept">
      <ul className="bullets">
        <li>Your account, jobs, shifts and vacations: until you delete them or your account.</li>
        <li>Signed-in sessions: 30 days after you last use them, or until you sign out, then deleted by a scheduled cleanup.</li>
        <li>Unfinished Google or LINE sign-ins: normally less than an hour.</li>
        <li>Email codes and unfinished email sign-ups: until used, or until they expire after 10 minutes and a scheduled cleanup deletes them.</li>
        <li>
          Retry keys: when you add a job, shift or vacation, a copy of the saved result is kept so a retried request can't save it
          twice. It expires after 24 hours and a scheduled cleanup deletes it.
        </li>
        <li>Rate-limit counters: about an hour, in memory only.</li>
        <li>Logs: up to 3 days.</li>
        <li>The database provider's restore history: up to 6 hours after a change.</li>
        <li>Copies of emails sent: in the sending Gmail mailbox until deleted there.</li>
      </ul>
    </Section>

    <Section title="Deleting your data">
      <ul className="bullets">
        <li>
          In Settings, choose Delete account. After you confirm it's you (your password, an emailed code, or a fresh Google or
          LINE sign-in), {NAME} deletes your account, jobs, shifts, vacations, linked sign-in methods, and the codes and retry keys
          linked to your account at once, and signs you out on every device.
        </li>
        <li>
          Some data is not linked to your account and is not deleted with it: an email code record that only holds your address
          (for example after someone tried to sign up with it) stays until it expires and a scheduled cleanup deletes it, and
          rate-limit counters stay in memory for about an hour.
        </li>
        <li>
          Deleted data can remain for up to 6 hours in the database provider's restore history, and in logs for up to 3 days.
          Copies of emails already sent stay in the sending Gmail mailbox.
        </li>
        <li>
          You can also delete single shifts and vacations, delete jobs without shifts (jobs with shifts can be archived), and unlink
          Google or LINE in Settings.
        </li>
        <li>
          To also remove {NAME} from your Google or LINE account: in Google, open{' '}
          <External href={GOOGLE_CONNECTIONS}>your third-party connections</External>; in LINE, go to Settings › Account › Authorized
          apps.
        </li>
        <li>For anything else, email <Contact />.</li>
      </ul>
    </Section>

    <Section title="Your choices">
      <p>
        You can see and change your name, username, email and sign-in methods in Settings, and your jobs, shifts and vacations in
        the app. If you email {NAME}, your message stays in that Gmail mailbox.
      </p>
    </Section>

    <Section title="Informational only">
      <p>
        {NAME}'s hour checks and pay figures help you plan. They are not legal advice. See the <Link to="/terms">Terms of Service</Link>.
      </p>
    </Section>

    <Section title="Changes">
      <p>Changes to this policy will be posted on this page with a new date.</p>
    </Section>

    <Section title="Contact">
      <p><Contact /></p>
    </Section>
  </LegalPage>
}

export function TermsScreen() {
  return <LegalPage title="Terms of Service">
    <p>
      These terms apply when you use {NAME}, on the web or installed on your device. By using it you accept them, so please read
      them. If you don't accept them, please don't use {NAME}.
    </p>

    <Section title="What it is">
      <p>
        {NAME} is a free planning tool, run by an individual developer, that helps international students in Japan track part-time
        hours across all their jobs against the limits for the Student status (28 hours in any 7 consecutive days, or 8 hours a
        day and 40 hours in any 7 days during the school's long vacations), and estimate their pay.
      </p>
    </Section>

    <Section title="Informational only — not legal advice">
      <ul className="bullets">
        <li>
          {NAME} is not legal, immigration, tax or employment advice, and it is not connected with the Immigration Services Agency
          of Japan, your school or your employers.
        </li>
        <li>
          Its results depend on what you enter and on how it reads the rules. Where the rules are unclear it uses the stricter
          reading and marks the result as ambiguous, but it can still be wrong or out of date.
        </li>
        <li>
          If you hold an individual permission, your limits may differ. Check the{' '}
          <External href={BRAND.officialRulesUrl}>official rules (ISA)</External>, and ask your school or the Immigration Services
          Agency if you're unsure.
        </li>
        <li>You are responsible for following the conditions of your own permission.</li>
        <li>
          Pay figures are estimates. Overtime is counted per job per day only, so they leave out overtime from weekly totals,
          legal-holiday premiums, the extra premium beyond 60 overtime hours a month, overtime that arises from combining hours
          across employers, taxes and deductions. Your employer's payslip is what counts.
        </li>
      </ul>
    </Section>

    <Section title="Your account">
      <ul className="bullets">
        <li>Give accurate information and keep your password and sign-in methods to yourself.</li>
        <li>You are responsible for what happens under your account. If you think someone else has used it, email <Contact />.</li>
        <li>Signing in with Google or LINE is also subject to their own terms.</li>
      </ul>
    </Section>

    <Section title="Your data">
      <p>
        What you enter stays yours. You allow {NAME} to store and process it only to provide the service to you, as the{' '}
        <Link to="/privacy">Privacy Policy</Link> describes. You can delete it at any time.
      </p>
    </Section>

    <Section title="Acceptable use">
      <p>Don't use {NAME} to:</p>
      <ul className="bullets">
        <li>break the law, or store other people's personal information without their permission;</li>
        <li>access or try to access anyone else's account or data;</li>
        <li>probe, attack, overload or get around the service's security or rate limits;</li>
        <li>create accounts or send requests in bulk by automated means, or send codes to addresses that aren't yours.</li>
      </ul>
    </Section>

    <Section title="Availability and changes">
      <p>
        {NAME} is free and runs on free hosting plans. It can be slow to start after it has been idle, may be unavailable at times,
        and may change or stop. These terms may be updated; changes will be posted on this page with a new date, and continuing
        to use {NAME} means you accept them.
      </p>
    </Section>

    <Section title="Ending your use">
      <p>
        You can stop using {NAME} and delete your account in Settings at any time. Accounts that break these terms or put the
        service or other people at risk may be suspended or deleted.
      </p>
    </Section>

    <Section title="No warranty">
      <p>
        {NAME} is provided as is, without warranties of any kind. To the extent the law allows, its developer is not liable for
        any loss or damage that comes from using {NAME} or relying on its results, including penalties, lost income or effects on
        your residence status. Nothing in these terms limits rights you have that the law does not allow to be limited.
      </p>
    </Section>

    <Section title="Contact">
      <p><Contact /></p>
    </Section>
  </LegalPage>
}
