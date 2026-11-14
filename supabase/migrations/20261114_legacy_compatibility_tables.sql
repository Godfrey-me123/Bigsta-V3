-- ============================================================================
-- BIGsta-V3 / amber-dog Database Migration
-- Target Database: amber-dog (fpkgkxyihbqlrsstvnnh)
-- Target App: Bigsta-V3 (Kotlin Native Android)
-- ============================================================================

-- Step 0: Ensure helper function is_bigsta_admin exists (checks user_roles)
CREATE OR REPLACE FUNCTION public.is_bigsta_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles
    WHERE user_id = auth.uid()
      AND LOWER(role) = 'admin'
  );
$$;

-- 1. Payment Submissions
CREATE TABLE IF NOT EXISTS public.payment_submissions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  payload jsonb DEFAULT '{}'::jsonb,
  status text NOT NULL DEFAULT 'pending',
  created_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_payment_submissions_user_id ON public.payment_submissions(user_id);
ALTER TABLE public.payment_submissions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can read own payment submissions" ON public.payment_submissions;
CREATE POLICY "Users can read own payment submissions" ON public.payment_submissions
  FOR SELECT TO authenticated USING (auth.uid() = user_id OR public.is_bigsta_admin());

-- 2. Payment Reviews
CREATE TABLE IF NOT EXISTS public.payment_reviews (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  submission_id uuid REFERENCES public.payment_submissions(id) ON DELETE CASCADE,
  admin_id uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  review_notes text,
  status text NOT NULL DEFAULT 'pending',
  reviewed_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_payment_reviews_submission_id ON public.payment_reviews(submission_id);
ALTER TABLE public.payment_reviews ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Admins can view payment reviews" ON public.payment_reviews;
CREATE POLICY "Admins can view payment reviews" ON public.payment_reviews
  FOR ALL TO authenticated USING (public.is_bigsta_admin());

-- 3. Template Categories
CREATE TABLE IF NOT EXISTS public.template_categories (
  id text PRIMARY KEY,
  category_name text NOT NULL,
  service_type text NOT NULL DEFAULT 'NIDA',
  created_at timestamptz DEFAULT now()
);
ALTER TABLE public.template_categories ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Anyone can view template categories" ON public.template_categories;
CREATE POLICY "Anyone can view template categories" ON public.template_categories
  FOR SELECT TO authenticated, anon USING (true);

-- 4. Template Elements
CREATE TABLE IF NOT EXISTS public.template_elements (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  template_id text NOT NULL,
  element_name text NOT NULL,
  element_type text NOT NULL DEFAULT 'text',
  element_json jsonb DEFAULT '{}'::jsonb,
  updated_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_template_elements_template_id ON public.template_elements(template_id);
ALTER TABLE public.template_elements ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Anyone can read template elements" ON public.template_elements;
CREATE POLICY "Anyone can read template elements" ON public.template_elements
  FOR SELECT TO authenticated, anon USING (true);

-- 5. Template Versions
CREATE TABLE IF NOT EXISTS public.template_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  template_id text NOT NULL,
  version_number integer NOT NULL DEFAULT 1,
  template_json jsonb DEFAULT '{}'::jsonb,
  created_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_template_versions_template_id ON public.template_versions(template_id);
ALTER TABLE public.template_versions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Anyone can read template versions" ON public.template_versions;
CREATE POLICY "Anyone can read template versions" ON public.template_versions
  FOR SELECT TO authenticated, anon USING (true);

-- 6. Token Wallets (Legacy alias; active balance in profiles.tokens)
CREATE TABLE IF NOT EXISTS public.token_wallets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  balance integer NOT NULL DEFAULT 0,
  updated_at timestamptz DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_token_wallets_user_id ON public.token_wallets(user_id);
ALTER TABLE public.token_wallets ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can read own token wallet" ON public.token_wallets;
CREATE POLICY "Users can read own token wallet" ON public.token_wallets
  FOR SELECT TO authenticated USING (auth.uid() = user_id OR public.is_bigsta_admin());

-- 7. Tokens (Legacy alias; active transactions in token_transactions)
CREATE TABLE IF NOT EXISTS public.tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  balance integer NOT NULL DEFAULT 0,
  updated_at timestamptz DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_tokens_user_id ON public.tokens(user_id);
ALTER TABLE public.tokens ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can read own tokens" ON public.tokens;
CREATE POLICY "Users can read own tokens" ON public.tokens
  FOR SELECT TO authenticated USING (auth.uid() = user_id OR public.is_bigsta_admin());

-- 8. User Settings
CREATE TABLE IF NOT EXISTS public.user_settings (
  user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  profile_settings jsonb DEFAULT '{}'::jsonb,
  preferences jsonb DEFAULT '{}'::jsonb,
  updated_at timestamptz DEFAULT now()
);
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own settings" ON public.user_settings;
CREATE POLICY "Users can manage own settings" ON public.user_settings
  FOR ALL TO authenticated USING (auth.uid() = user_id);

-- Step 9: Safe Realtime Publication Registration
-- Adds tables to supabase_realtime if they are not already members
DO $$
DECLARE
  tbl text;
  tables text[] := ARRAY[
    'payment_requests',
    'nida_requests',
    'profiles',
    'user_feedback',
    'sms_logs',
    'payment_submissions',
    'token_wallets',
    'user_settings'
  ];
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    FOREACH tbl IN ARRAY tables LOOP
      BEGIN
        EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', tbl);
      EXCEPTION
        WHEN duplicate_object THEN
          -- Table is already in the publication, ignore
          NULL;
        WHEN undefined_table THEN
          -- Table does not exist in schema, ignore
          NULL;
        WHEN OTHERS THEN
          -- Catch any other publication error safely
          NULL;
      END;
    END LOOP;
  END IF;
END $$;
