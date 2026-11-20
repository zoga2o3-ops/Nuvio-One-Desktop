package com.liskovsoft.youtubeapi.app.potokennp2.misc

import com.liskovsoft.youtubeapi.app.potokennp2.core.PoTokenGenerator
import com.liskovsoft.youtubeapi.app.potokennp2.generators.PoTokenV8

internal fun selectFactory(): PoTokenGenerator.Factory = PoTokenV8
