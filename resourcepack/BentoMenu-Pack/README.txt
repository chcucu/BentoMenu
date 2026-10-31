BentoMenu optional resource pack

1. Put this folder (or zip) on your web server / CDN.
2. Set ui.resource-pack: true in BentoMenu config.yml.
3. Themes use resource-pack-material: LEATHER_HORSE_ARMOR with model data 9001,
   which keeps per-slot RGB tinting for true gradients.
4. Icons use custom-model-data 9101-9108 on vanilla materials.

Without this pack BentoMenu falls back to vanilla stained glass; all menus remain usable.