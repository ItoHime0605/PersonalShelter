# PersonalShelter 1.0.10 stability candidate (NeoForge 1.21.1)

- Server-side container checks run only on the server thread and before vanilla consumes client prediction.
- Remove duplicate menu-close inventory mutation and allow vanilla to return cursor and recipe inputs. Inventory-full overflow follows vanilla safe restitution, including dropping items if necessary.
- Owner permissions and conservative visitor storage rules remain unchanged.
- Time sync packets use the effective local daylight rule both at dimension entry and during periodic synchronization. Follow-overworld interpolation uses the real overworld clock.
- Moon rendering uses a separate current-dimension packet, sent on change; controller snapshots no longer drive rendering.
- Independent game time and weather logic are deliberately unchanged until reproduction demonstrates a need.

## Regression checklist (dedicated server, matching client)
1. Owner and visitor: ordinary crafting, shift-click output, recipe book, full inventory, cursor occupied when closing. Verify counts before/after, including dropped overflow; no duplication.
2. Visitor without DROP_ITEM: Q and Ctrl+Q blocked, normal permitted crafting works. Test a shared chest with each PUT/TAKE permission and ensure denied actions revert.
3. Test Farmer's Delight and other modded workstations with existing permissions. Unknown processing menus remain conservative.
4. Freeze day at sunrise/ noon/ sunset, observe client sun and shadows for at least 60 s; repeat after reconnect and switching dimension. Test Iris enabled/disabled, same shader pack.
5. Visit another player's land with a different moon phase, change controller settings while visiting, verify rendering still matches current land.
6. Verify weather, expansion, existing friend permissions, existing saved land, and machine processing after restart.
7. Compare client/server logs for exceptions and incorrect item counts. This is a candidate, not a verified production build.
