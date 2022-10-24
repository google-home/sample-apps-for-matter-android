# Google Home Sample App for Matter

The **Google Home Sample App for Matter** (GHSA for Matter) provides a concrete example of how to
use the [Home Mobile SDK](https://developers.home.google.com/matter/apis/home) to make it
easy to commission and share [Matter](https://developers.home.google.com/matter/overview) devices
across Apps and ecosystems. It also demonstrates how to use commissioning and Cluster libraries
from the
[Matter repo (`connectedhomeip`)](https://github.com/project-chip/connectedhomeip).

This is a sample application that can be used as the starting point to build your
smart home application for Matter devices.

It can also be used as a learning tool to better understand key Matter concepts, as well as a tool
to debug and troubleshoot interactions with Matter devices.

> **Warning:** There is still work to be done on this sample app, but we are making it available
> at this point to allow developers to get a headstart on learning how to use the Home Mobile SDK.

## Clone the repository

The Sample app GitHub repository includes third party libraries from the
[Matter repo (`connectedhomeip`)](https://github.com/project-chip/connectedhomeip).
These native libraries are over 50MB, and require the use of Git Large File
Storage (LFS).

To clone the repository, complete the following steps:

1.  Install [Git LFS](https://git-lfs.github.com/).

2.  Initialize Git LFS.

    ```console
    $ git lfs install
    ```

    When complete, the console displays the following:

    ```console
    Updated Git hooks.
    Git LFS initialized.
    ```

3.  Once Git LFS is installed and initialized, you're ready to clone the
    repository. When cloning completes, Git checks out the `main` branch
    and downloads the native libraries for you.

    ```console
    $ git clone https://github.com/google-home/sample-app-for-matter-android.git
    ```

## Releases

There are multiple releases available for this sample app. Before proceding, make sure you
download the correct one for your use case.

*   **Release [v1.0.3](https://github.com/google-home/sample-app-for-matter-android/releases/tag/v1.0.3)**
    — Use this release with the [Google Developer Preview](https://developers.home.google.com/matter/get-started).
*   **Release [v1.1.0](https://github.com/google-home/sample-app-for-matter-android/releases/tag/v1.1.0)**
    — This release is intended for Google testing use. Do NOT use it with the Google Developer Preview.
*   **Release [v1.2.0](https://github.com/google-home/sample-app-for-matter-android/releases/tag/v1.2.0)**
    — This release is intended for Google testing use. Do NOT use it with the Google Developer Preview.  

## Get started

*   To make sure that your device has the latest Matter support, review the
    [Verify Matter Modules & Services](https://developers.home.google.com/matter/verify-services)
    guide.
*   Build a Matter device with On/Off capabilities. This sample  app works with a virtual device
    and an ESP32.
    *   [Build a Matter Virtual Device](https://developers.home.google.com/codelabs/matter-device-virtual)
        with the `rootnode_dimmablelight_bCwGYSDpoe` app. When you
        [Create a Matter integration](https://developers.home.google.com/matter/integration/create)
        in the [Home Developer Console](https://console.home.google.com/projects),
        use `0xFFF1` as your Vendor ID and `0x8000` as your Product ID.
    *   [Build an Espressif Device](https://developers.home.google.com/matter/vendors/espressif)
        with the `all-clusters-app`. When you
        [Create a Matter integration](https://developers.home.google.com/matter/integration/create)
        in the [Home Developer Console](https://console.home.google.com/projects),
        use `0xFFF1` as your Vendor ID and `0x8001` as your Product ID.
*   For an overview of the user interface and features, refer to
    the [Google Home Sample App for Matter Guide](https://developers.home.google.com/samples/matter-app).
*   To review code samples and start building, refer to
    the [Build an Android App for Matter](https://developers.home.google.com/codelabs/matter-sample-app)
    Codelab.        

## Version

Google Home Sample App for Matter follows the [Semantic](http://semver.org/)
and [Android](https://developer.android.com/studio/publish/versioning) versioning guidelines for
release cycle transparency and to maintain backwards compatibility.

## License

Google Home Sample App for Matter is licensed under
the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Only use the Google Home Sample App for Matter name and marks when accurately referencing this
software distribution. Do not use the marks in a way that suggests you are endorsed by or otherwise
affiliated with Nest, Google, or the Connectivity Standards Alliance (CSA).
