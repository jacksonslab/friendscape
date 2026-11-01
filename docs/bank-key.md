# Bank Key

The Bank Key is an untradeable, unlimited-use item. Choose **Summon** to create one
personal banker beside you, then choose **Bank** on that NPC. Other players cannot
use your banker. Opening a bank removes your summon. Repeated summons replace the
previous NPC; logout, moving more than 15 tiles away, or 100 ticks (60 seconds)
without using it also removes the NPC. Normal bank access restrictions still apply.

Each character receives one key in their bank on their next login after deployment.
A permanent server-only varp records delivery. A full bank defers delivery until an
unlocked slot becomes available; existing items are never overwritten. Characters
created later receive the same grant. This change does not modify live saves before
deployment. An existing key in another inventory also counts as delivered.

Rename the banker by editing the NPC `name` in
`content/other/bank-key/pack/src/main/resources/pack/configs/bank_key.toml`.
This is a dedicated NPC type and does not rename ordinary bankers. Rebuild the
cache after changing its name or icon.

The original gold key with a teal accent is packed as `models.friendscape_bank_key`.
Its reproducible model source is `tools/scripts/build_bank_key_model.py`.

Build the cache with `gradlew :or-cache:buildCache` before building the server.
Run focused checks with `gradlew :content:other:bank-key:test`.
Deploy the matching cache and server together when release is authorized.
