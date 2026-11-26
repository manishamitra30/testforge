-- TestForge demo data: two realistic projects with a month of runs, flaky tests and defects.
--
-- Usage (PowerShell, repo root, database container running):
--   Get-Content docs\demo-data.sql | docker exec -i testforge-db psql -U testforge -d testforge -v ON_ERROR_STOP=1 -v username=YOUR_LOGIN_USERNAME
--
-- YOUR_LOGIN_USERNAME is the account you registered in the app. The data is attached to that account.
-- Safe to run twice: it does nothing if the demo project already exists.

SELECT set_config('demo.username', :'username', false);

DO $$
DECLARE
    uid bigint;
    p1 bigint;
    p2 bigint;
    s_auth bigint;
    s_checkout bigint;
    s_search bigint;
    s_profile bigint;
    s_wallet bigint;
    run_suites bigint[];
    run_days int[] := ARRAY[27, 24, 21, 18, 15, 12, 10, 8, 6, 4, 2, 1];
    rid bigint;
    c record;
    run_no int;
    checkout_idx int;
    score int;
    threshold int;
    res_status text;
BEGIN
    SELECT id INTO uid FROM users WHERE username = current_setting('demo.username');
    IF uid IS NULL THEN
        RAISE EXCEPTION 'User "%" not found. Register it in the app first, then run this again.', current_setting('demo.username');
    END IF;
    IF EXISTS (SELECT 1 FROM projects WHERE owner_id = uid AND name = 'ShopEasy Web') THEN
        RAISE NOTICE 'Demo data already exists - nothing to do.';
        RETURN;
    END IF;

    -- ---------- Project 1: ShopEasy Web ----------
    INSERT INTO projects (name, description, owner_id, created_at)
    VALUES ('ShopEasy Web', 'E-commerce storefront: sign-in, search, checkout and profile.', uid, now() - interval '30 days')
    RETURNING id INTO p1;

    INSERT INTO test_suites (project_id, name, description) VALUES (p1, 'Authentication', 'Sign-in, registration and password reset') RETURNING id INTO s_auth;
    INSERT INTO test_suites (project_id, name, description) VALUES (p1, 'Checkout', 'Cart, coupons and payment') RETURNING id INTO s_checkout;
    INSERT INTO test_suites (project_id, name, description) VALUES (p1, 'Search & Filters', 'Finding and sorting products') RETURNING id INTO s_search;
    INSERT INTO test_suites (project_id, name, description) VALUES (p1, 'Profile', 'Account and address management') RETURNING id INTO s_profile;

    INSERT INTO test_cases (suite_id, title, priority, status, tags, created_by, created_at, updated_at)
    SELECT s_auth, v.title, v.priority, v.status, v.tags, uid, now() - interval '28 days', now() - interval '5 days'
    FROM (VALUES
        ('Login with valid credentials', 'CRITICAL', 'READY', 'smoke,auth'),
        ('Login with wrong password shows error', 'HIGH', 'READY', 'auth,negative'),
        ('Login with unregistered email', 'MEDIUM', 'READY', 'auth,negative'),
        ('Account locks after 5 failed attempts', 'HIGH', 'READY', 'auth,security'),
        ('Password reset email is sent', 'HIGH', 'READY', 'auth'),
        ('Reset link expires after 30 minutes', 'MEDIUM', 'READY', 'auth,security'),
        ('Register with valid details', 'CRITICAL', 'READY', 'smoke,auth'),
        ('Register rejects weak password', 'MEDIUM', 'DRAFT', 'auth,negative')
    ) AS v(title, priority, status, tags);

    INSERT INTO test_cases (suite_id, title, priority, status, tags, created_by, created_at, updated_at)
    SELECT s_checkout, v.title, v.priority, v.status, v.tags, uid, now() - interval '28 days', now() - interval '4 days'
    FROM (VALUES
        ('Add item to cart', 'CRITICAL', 'READY', 'smoke,cart'),
        ('Update item quantity in cart', 'HIGH', 'READY', 'cart'),
        ('Remove item from cart', 'MEDIUM', 'READY', 'cart'),
        ('Apply valid discount coupon', 'HIGH', 'READY', 'coupon'),
        ('Expired coupon is rejected', 'MEDIUM', 'READY', 'coupon,negative'),
        ('Pay by credit card', 'CRITICAL', 'READY', 'smoke,payment'),
        ('Pay by UPI', 'HIGH', 'READY', 'payment'),
        ('Guest checkout', 'HIGH', 'READY', 'checkout'),
        ('Order confirmation email is sent', 'MEDIUM', 'READY', 'checkout,email'),
        ('Cash on delivery above 50,000 is blocked', 'LOW', 'DEPRECATED', 'payment')
    ) AS v(title, priority, status, tags);

    INSERT INTO test_cases (suite_id, title, priority, status, tags, created_by, created_at, updated_at)
    SELECT s_search, v.title, v.priority, v.status, v.tags, uid, now() - interval '26 days', now() - interval '6 days'
    FROM (VALUES
        ('Search by product name', 'CRITICAL', 'READY', 'smoke,search'),
        ('Search with no results shows a message', 'LOW', 'READY', 'search,negative'),
        ('Filter by price range', 'HIGH', 'READY', 'filters'),
        ('Filter by brand', 'MEDIUM', 'READY', 'filters'),
        ('Sort by price, low to high', 'MEDIUM', 'READY', 'sort'),
        ('Sort by rating', 'LOW', 'READY', 'sort'),
        ('Pagination keeps active filters', 'MEDIUM', 'DRAFT', 'filters')
    ) AS v(title, priority, status, tags);

    INSERT INTO test_cases (suite_id, title, priority, status, tags, created_by, created_at, updated_at)
    SELECT s_profile, v.title, v.priority, v.status, v.tags, uid, now() - interval '25 days', now() - interval '7 days'
    FROM (VALUES
        ('Update display name', 'LOW', 'READY', 'profile'),
        ('Changing email requires verification', 'MEDIUM', 'READY', 'profile,security'),
        ('Add a shipping address', 'HIGH', 'READY', 'profile,address'),
        ('Delete a saved address', 'LOW', 'READY', 'profile,address'),
        ('Profile photo over 5 MB is rejected', 'LOW', 'READY', 'profile,negative')
    ) AS v(title, priority, status, tags);

    -- a few cases with steps
    INSERT INTO test_steps (test_case_id, step_number, action, expected_result)
    SELECT tc.id, s.n, s.action, s.expected
    FROM test_cases tc
    JOIN (VALUES
        ('Login with valid credentials', 1, 'Open the login page', 'The email and password fields are shown'),
        ('Login with valid credentials', 2, 'Enter a registered email and the correct password', 'The Sign in button becomes enabled'),
        ('Login with valid credentials', 3, 'Click Sign in', 'The user lands on the home page and sees their name'),
        ('Pay by credit card', 1, 'Add any product to the cart and open checkout', 'The order summary is shown'),
        ('Pay by credit card', 2, 'Enter a valid test card and confirm', 'The payment is accepted'),
        ('Pay by credit card', 3, 'Check the order page', 'The order shows as Paid with an order number'),
        ('Apply valid discount coupon', 1, 'Add a 1,000 item to the cart', 'Cart total is 1,000'),
        ('Apply valid discount coupon', 2, 'Enter coupon SAVE10 and apply', 'Cart total becomes 900')
    ) AS s(title, n, action, expected) ON s.title = tc.title
    WHERE tc.suite_id IN (s_auth, s_checkout);

    -- twelve completed runs over four weeks; the pass rate improves, two checkout tests flip between pass and fail
    run_suites := ARRAY[s_auth, s_checkout, s_search, s_checkout, s_auth, s_checkout, s_search, s_checkout, s_auth, s_checkout, s_search, s_checkout];
    checkout_idx := 0;
    FOR run_no IN 1..12 LOOP
        INSERT INTO test_runs (project_id, suite_id, name, type, status, started_at, finished_at, triggered_by)
        VALUES (
            p1, run_suites[run_no], 'Regression #' || run_no,
            CASE WHEN run_no % 4 = 0 THEN 'AUTOMATED' ELSE 'MANUAL' END,
            'COMPLETED',
            (current_date - run_days[run_no]) + time '10:00',
            (current_date - run_days[run_no]) + time '10:40',
            uid)
        RETURNING id INTO rid;

        IF run_suites[run_no] = s_checkout THEN
            checkout_idx := checkout_idx + 1;
        END IF;
        threshold := 4 + run_no / 2;

        FOR c IN
            SELECT id, title, row_number() OVER (ORDER BY id) AS n
            FROM test_cases
            WHERE suite_id = run_suites[run_no] AND status <> 'DEPRECATED'
        LOOP
            score := (c.n * 7 + run_no * 13) % 10;
            res_status := CASE
                WHEN score < threshold THEN 'PASSED'
                WHEN score - threshold <= 1 THEN 'FAILED'
                WHEN score - threshold = 2 THEN 'BLOCKED'
                ELSE 'SKIPPED' END;
            IF c.title IN ('Apply valid discount coupon', 'Guest checkout') THEN
                res_status := CASE WHEN checkout_idx % 2 = 0 THEN 'FAILED' ELSE 'PASSED' END;
            END IF;

            INSERT INTO test_run_results (run_id, test_case_id, status, duration_ms, error_message, executed_by)
            VALUES (rid, c.id, res_status, 800 + (c.n * 137 + run_no * 53) % 4000,
                    CASE WHEN res_status = 'FAILED' THEN 'Assertion failed: actual value did not match the expected result' END,
                    uid);
        END LOOP;
    END LOOP;

    -- one run still in progress today
    INSERT INTO test_runs (project_id, suite_id, name, type, status, started_at, triggered_by)
    VALUES (p1, s_checkout, 'Sprint 14 checkout check', 'MANUAL', 'IN_PROGRESS', now() - interval '20 minutes', uid)
    RETURNING id INTO rid;
    INSERT INTO test_run_results (run_id, test_case_id, status, duration_ms, executed_by)
    SELECT rid, id,
           CASE WHEN row_number() OVER (ORDER BY id) <= 4 THEN 'PASSED' ELSE 'NOT_RUN' END,
           CASE WHEN row_number() OVER (ORDER BY id) <= 4 THEN 1500 ELSE 0 END,
           CASE WHEN row_number() OVER (ORDER BY id) <= 4 THEN uid END
    FROM test_cases WHERE suite_id = s_checkout AND status <> 'DEPRECATED';

    -- defects, linked to the latest failed result of the related test where there is one
    INSERT INTO defects (project_id, test_result_id, title, description, severity, status, assignee_id, reporter_id, created_at, updated_at)
    SELECT p1,
           (SELECT r.id FROM test_run_results r JOIN test_cases tc ON tc.id = r.test_case_id
             WHERE tc.title = v.case_title AND r.status = 'FAILED'
               AND tc.suite_id IN (SELECT id FROM test_suites WHERE project_id = p1)
             ORDER BY r.id DESC LIMIT 1),
           v.title, v.descr, v.severity, v.status,
           CASE WHEN v.assigned THEN uid END, uid,
           now() - (v.days_ago || ' days')::interval, now() - (v.days_ago || ' days')::interval
    FROM (VALUES
        ('Apply valid discount coupon', 'Coupon is not applied on the first attempt', 'The discount only appears after pressing Apply a second time.', 'HIGH', 'OPEN', false, 2),
        ('Guest checkout', 'Guest checkout fails when the address line 2 is empty', 'Submitting the form returns a generic error and no order is created.', 'HIGH', 'IN_PROGRESS', true, 3),
        ('Pay by UPI', 'UPI payment times out after 30 seconds', 'The payment page spins and never returns to the shop.', 'CRITICAL', 'OPEN', true, 1),
        ('Filter by price range', 'Price filter ignores the upper bound', 'Products above the maximum price still appear in the results.', 'MEDIUM', 'RESOLVED', true, 9),
        ('Reset link expires after 30 minutes', 'Password reset link still works after it expires', 'A link older than 30 minutes is accepted.', 'CRITICAL', 'IN_PROGRESS', true, 6),
        ('Login with wrong password shows error', 'Error message reveals whether the email is registered', 'Wrong password and unknown email show different messages.', 'HIGH', 'CLOSED', false, 14),
        ('Sort by rating', 'Sort by rating lists unrated products first', 'Products without ratings appear above rated ones.', 'LOW', 'OPEN', false, 4),
        ('Profile photo over 5 MB is rejected', 'Photo upload hangs on a slow network', 'The progress bar stops at 90 percent.', 'LOW', 'RESOLVED', true, 11)
    ) AS v(case_title, title, descr, severity, status, assigned, days_ago);

    -- ---------- Project 2: PayWallet Mobile ----------
    INSERT INTO projects (name, description, owner_id, created_at)
    VALUES ('PayWallet Mobile', 'Android wallet app: top-up, send money and history.', uid, now() - interval '12 days')
    RETURNING id INTO p2;
    INSERT INTO test_suites (project_id, name, description) VALUES (p2, 'Wallet', 'Core wallet flows') RETURNING id INTO s_wallet;
    INSERT INTO test_cases (suite_id, title, priority, status, tags, created_by)
    SELECT s_wallet, v.title, v.priority, 'READY', v.tags, uid
    FROM (VALUES
        ('Top up wallet from a saved card', 'CRITICAL', 'smoke,wallet'),
        ('Send money to a contact', 'CRITICAL', 'smoke,transfer'),
        ('Send money above the daily limit is blocked', 'HIGH', 'transfer,negative'),
        ('Transaction history shows the latest first', 'MEDIUM', 'history'),
        ('Download monthly statement', 'LOW', 'history'),
        ('Biometric unlock after app restart', 'HIGH', 'security')
    ) AS v(title, priority, tags);
    INSERT INTO test_runs (project_id, suite_id, name, type, status, started_at, finished_at, triggered_by)
    VALUES (p2, s_wallet, 'Release 1.2 smoke', 'MANUAL', 'COMPLETED', (current_date - 3) + time '15:00', (current_date - 3) + time '15:30', uid)
    RETURNING id INTO rid;
    INSERT INTO test_run_results (run_id, test_case_id, status, duration_ms, error_message, executed_by)
    SELECT rid, id,
           CASE WHEN title LIKE 'Send money above%' THEN 'FAILED' WHEN title LIKE 'Download%' THEN 'SKIPPED' ELSE 'PASSED' END,
           1200,
           CASE WHEN title LIKE 'Send money above%' THEN 'Transfer above the limit was accepted' END,
           uid
    FROM test_cases WHERE suite_id = s_wallet;
    INSERT INTO defects (project_id, test_result_id, title, description, severity, status, reporter_id, created_at, updated_at)
    SELECT p2, r.id, 'Daily transfer limit is not enforced', 'A transfer above the daily limit goes through.', 'CRITICAL', 'OPEN', uid, now() - interval '3 days', now() - interval '3 days'
    FROM test_run_results r JOIN test_cases tc ON tc.id = r.test_case_id
    WHERE tc.suite_id = s_wallet AND r.status = 'FAILED' LIMIT 1;

    RAISE NOTICE 'Demo data created for user %.', current_setting('demo.username');
END
$$;
