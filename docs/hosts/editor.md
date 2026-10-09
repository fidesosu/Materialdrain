# The config editor

Every config can be written by hand, but you rarely need to: the app has an editor that knows every field, says what
each one means, and checks the config before it's saved. It opens when you add a host, and from the pencil on a host's
card.

## Starting a new host

Under **Settings → Advanced → Add a custom host**:

- **New host** has a button per kind. Each one opens the editor on a complete config of that kind, with every field
  the app reads, at its default or at an example value. Change the address (and whatever else differs), then **Add
  host**.

    | Button | Kind | Starts from |
    | --- | --- | --- |
    | REST API (from Pixeldrain) | `generic_rest` | The Pixeldrain config, which has nearly every endpoint, to adapt to another API |
    | WebDAV | `webdav` | A Nextcloud account |
    | S3 | `s3` | A MinIO bucket |
    | SMB | `smb` | A share on a NAS |

    The WebDAV, S3 and SMB templates are made from the app's own description of the format, so they always match the
    installed version. A field that's `null` or empty in a template is just unset: leave it, or remove it.

- **Or paste a config** takes a config someone shared. **Add host** adds it straight away; **Review first** opens it
  in the editor, so you can look it over (and see where it would send your sign-in) before adding it.

The new host becomes the active one. Then open its card and enter the [sign-in](sign-in.md).

## Two views of one config

The editor has a **Form** view and a **JSON** view, switched at the top. Both edit the same config, and switching
keeps what you typed.

=== "Form"

    The config in sections, each folded away until opened:

    | Section | Has |
    | --- | --- |
    | **Basics** | The name and the address; for a REST API also the start folder and the sign-in switches |
    | **Sign-in** | How the sign-in is sent (`auth`). Never the key or password themselves |
    | **Endpoints** | REST API only: one card per request, grouped by the tab or feature it unlocks, with the placeholders each can use. **Add an endpoint** lists the ones still missing |
    | **Screens** | Which tabs show, in what order: leave it to the app, or **Choose the tabs myself**, with a **Tab name** and actions to **Leave out on this tab** for each |
    | **Sharing and updates** | The `meta` fields: id, version, author, update address, lowest app version |

    Each field says what it means and what it is when left out. Only the important fields and the ones already set
    are shown; the rest of what the kind has is under **More options**, a tap away.

    Fields the app doesn't know (a note, a field for a newer version) are listed under **Other fields**, with their
    values. They're kept as they are.

=== "JSON"

    The config's text, as it's saved and exported. Handy for pasting in a part from elsewhere, or for seeing the whole
    thing at once.

    While the text isn't valid JSON, the editor says so (*Not valid JSON yet: the last valid value is kept*), and the
    form shows the last version that was.

## Saving

Saving checks the config the same way adding one does. If it can't be used yet (a required field is missing, a value
has the wrong type), the reason is shown at the top and nothing is saved.

Nothing is lost on the way:

- A config saved without changes keeps its own text, down to the spacing.
- One changed in the form is written out again, indented, with every field it had, including the ones under **Other
  fields**.
- Closing the editor with unsaved changes asks first (**Discard** / **Keep editing**).

Editing a config that updates from somewhere marks it **Edited**. The next update would replace your edits, so the app
says so, and turns auto-update off for it; see [Edits and updates](sharing-and-updates.md#edits-and-updates).

## The host's card

The rest of what you do with a host is on its card:

| | |
| --- | --- |
| **Pencil** | Opens the editor |
| **Sign-in** / **Credentials** | The key, the username and password, or **Sign in**; see [Sign-in](sign-in.md) |
| **Save & test** / **Test connection** | Saves the sign-in and tries the host, with a line for each check |
| **Check for updates** | When the config has an update address; see [Sharing and updates](sharing-and-updates.md) |
| **Export** | Copies the config's text, ready to share. It never has your sign-in in it |
| **Revert update** | After an update: brings back the version before it |
| **Remove** | Removes the host and its sign-in. The active host falls back to the built-in Pixeldrain |
